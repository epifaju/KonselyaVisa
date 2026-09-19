package com.konselyavisa.appointment;

import com.konselyavisa.appointment.api.AppointmentHoldResponse;
import com.konselyavisa.appointment.api.AppointmentMapper;
import com.konselyavisa.appointment.api.AppointmentResponse;
import com.konselyavisa.appointment.api.AppointmentSlotResponse;
import com.konselyavisa.appointment.api.BookAppointmentRequest;
import com.konselyavisa.appointment.api.CreateSlotRequest;
import com.konselyavisa.appointment.api.HoldAppointmentRequest;
import com.konselyavisa.appointment.domain.AppointmentSlot;
import com.konselyavisa.appointment.domain.AppointmentSlotStatus;
import com.konselyavisa.appointment.domain.AppointmentStatus;
import com.konselyavisa.appointment.domain.CaseAppointment;
import com.konselyavisa.appointment.persistence.AppointmentSlotRepository;
import com.konselyavisa.appointment.persistence.CaseAppointmentRepository;
import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.common.i18n.LocalizedText;
import com.konselyavisa.dossier.CaseFile;
import com.konselyavisa.dossier.CaseService;
import com.konselyavisa.dossier.CaseStatus;
import com.konselyavisa.outbox.OutboxAppender;
import com.konselyavisa.redis.KonselyaRedisProperties;
import com.konselyavisa.redis.SlotHoldStore;
import com.konselyavisa.tenancy.TenantContext;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentService {

    private final CaseService caseService;
    private final AppointmentSlotRepository slotRepository;
    private final CaseAppointmentRepository appointmentRepository;
    private final OutboxAppender outboxAppender;
    private final AppointmentMapper appointmentMapper;
    private final SlotHoldStore slotHoldStore;
    private final KonselyaRedisProperties redisProperties;

    public AppointmentService(
            CaseService caseService,
            AppointmentSlotRepository slotRepository,
            CaseAppointmentRepository appointmentRepository,
            OutboxAppender outboxAppender,
            AppointmentMapper appointmentMapper,
            SlotHoldStore slotHoldStore,
            KonselyaRedisProperties redisProperties) {
        this.caseService = caseService;
        this.slotRepository = slotRepository;
        this.appointmentRepository = appointmentRepository;
        this.outboxAppender = outboxAppender;
        this.appointmentMapper = appointmentMapper;
        this.slotHoldStore = slotHoldStore;
        this.redisProperties = redisProperties;
    }

    @Transactional
    public AppointmentSlotResponse createSlot(CreateSlotRequest request) {
        requireOrganization();
        LocalizedText.requireDefaultLocale(request.locationI18n(), "error.catalog.i18n_required");
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw BusinessException.badRequest("error.appointment.window_invalid");
        }
        int capacity = request.capacity() < 1 ? 1 : request.capacity();
        AppointmentSlot slot = new AppointmentSlot();
        slot.setStartsAt(request.startsAt());
        slot.setEndsAt(request.endsAt());
        slot.setCapacity(capacity);
        slot.setLocationI18n(new HashMap<>(request.locationI18n()));
        slot.setStatus(AppointmentSlotStatus.OPEN);
        slotRepository.saveAndFlush(slot);
        return appointmentMapper.toSlotResponse(slot, capacity);
    }

    @Transactional(readOnly = true)
    public List<AppointmentSlotResponse> listSlots(Instant from, Instant to) {
        requireOrganization();
        Instant start = from == null ? Instant.now() : from;
        Instant end = to == null ? start.plus(30, ChronoUnit.DAYS) : to;
        return slotRepository
                .findByStatusAndStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAtAsc(
                        AppointmentSlotStatus.OPEN, start, end)
                .stream()
                .map(slot -> appointmentMapper.toSlotResponse(slot, visibleRemaining(slot)))
                .toList();
    }

    @Transactional
    public AppointmentHoldResponse hold(UUID caseId, HoldAppointmentRequest request) {
        CaseFile caseFile = caseService.requireAccessible(caseId);
        assertCaseBookable(caseFile);
        AppointmentSlot slot = slotRepository
                .findById(request.slotId())
                .orElseThrow(() -> BusinessException.notFound("error.appointment.slot_not_found"));
        assertSlotBookable(slot);
        acquireHold(slot, caseId);
        return new AppointmentHoldResponse(slot.getId(), caseId, Instant.now().plus(holdTtl()));
    }

    @Transactional
    public AppointmentResponse book(UUID caseId, BookAppointmentRequest request) {
        CaseFile caseFile = caseService.requireAccessible(caseId);
        assertCaseBookable(caseFile);
        if (appointmentRepository.existsByCaseFile_IdAndStatus(caseId, AppointmentStatus.BOOKED)) {
            throw BusinessException.conflict("error.appointment.already_booked");
        }
        AppointmentSlot slot = slotRepository
                .lockById(request.slotId())
                .orElseThrow(() -> BusinessException.notFound("error.appointment.slot_not_found"));
        assertSlotBookable(slot);
        acquireHold(slot, caseId);
        if (dbRemaining(slot) <= 0) {
            slotHoldStore.release(requireOrganization(), slot.getId(), caseId);
            throw BusinessException.conflict("error.appointment.slot_full");
        }
        CaseAppointment appointment = new CaseAppointment();
        appointment.setCaseFile(caseFile);
        appointment.setSlot(slot);
        appointment.setStatus(AppointmentStatus.BOOKED);
        appointmentRepository.saveAndFlush(appointment);
        if (caseFile.getStatus() == CaseStatus.CREATED) {
            caseFile.setStatus(CaseStatus.IN_PROGRESS);
        }
        slotHoldStore.release(requireOrganization(), slot.getId(), caseId);
        outboxAppender.appendAppointmentBooked(caseId, appointmentPayload(appointment));
        return appointmentMapper.toResponse(appointment, visibleRemaining(slot));
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> listByCase(UUID caseId) {
        caseService.requireAccessible(caseId);
        return appointmentRepository.findByCaseFile_IdOrderByCreatedAtDesc(caseId).stream()
                .map(appointment -> appointmentMapper.toResponse(appointment, visibleRemaining(appointment.getSlot())))
                .toList();
    }

    @Transactional
    public AppointmentResponse cancel(UUID appointmentId) {
        CaseAppointment appointment = appointmentRepository
                .findDetailedById(appointmentId)
                .orElseThrow(() -> BusinessException.notFound("error.appointment.not_found"));
        caseService.requireAccessible(appointment.getCaseFile().getId());
        if (appointment.getStatus() != AppointmentStatus.BOOKED) {
            throw BusinessException.badRequest("error.appointment.not_cancellable");
        }
        appointment.setStatus(AppointmentStatus.CANCELLED);
        outboxAppender.appendAppointmentCancelled(
                appointment.getCaseFile().getId(), appointmentPayload(appointment));
        return appointmentMapper.toResponse(appointment, visibleRemaining(appointment.getSlot()));
    }

    @Transactional
    public AppointmentSlotResponse cancelSlot(UUID slotId) {
        requireOrganization();
        AppointmentSlot slot = slotRepository
                .lockById(slotId)
                .orElseThrow(() -> BusinessException.notFound("error.appointment.slot_not_found"));
        if (slot.getStatus() == AppointmentSlotStatus.CANCELLED) {
            return appointmentMapper.toSlotResponse(slot, 0);
        }
        slot.setStatus(AppointmentSlotStatus.CANCELLED);
        for (CaseAppointment appointment :
                appointmentRepository.findBySlot_IdAndStatus(slotId, AppointmentStatus.BOOKED)) {
            appointment.setStatus(AppointmentStatus.CANCELLED);
            outboxAppender.appendAppointmentCancelled(
                    appointment.getCaseFile().getId(), appointmentPayload(appointment));
        }
        return appointmentMapper.toSlotResponse(slot, 0);
    }

    private void assertSlotBookable(AppointmentSlot slot) {
        if (slot.getStatus() != AppointmentSlotStatus.OPEN) {
            throw BusinessException.badRequest("error.appointment.slot_closed");
        }
        if (!slot.getStartsAt().isAfter(Instant.now())) {
            throw BusinessException.badRequest("error.appointment.slot_past");
        }
    }

    private void assertCaseBookable(CaseFile caseFile) {
        if (caseFile.getStatus() == CaseStatus.CANCELLED || caseFile.getStatus() == CaseStatus.COMPLETED) {
            throw BusinessException.badRequest("error.case.not_modifiable");
        }
        if (appointmentRepository.existsByCaseFile_IdAndStatus(caseFile.getId(), AppointmentStatus.BOOKED)) {
            throw BusinessException.conflict("error.appointment.already_booked");
        }
    }

    private void acquireHold(AppointmentSlot slot, UUID caseId) {
        int seats = dbRemaining(slot);
        if (seats <= 0) {
            throw BusinessException.conflict("error.appointment.slot_full");
        }
        if (!slotHoldStore.tryAcquire(requireOrganization(), slot.getId(), caseId, seats, holdTtl())) {
            throw BusinessException.conflict("error.appointment.hold_unavailable");
        }
    }

    private int visibleRemaining(AppointmentSlot slot) {
        return Math.max(0, dbRemaining(slot) - slotHoldStore.activeCount(requireOrganization(), slot.getId()));
    }

    private int dbRemaining(AppointmentSlot slot) {
        if (slot.getStatus() != AppointmentSlotStatus.OPEN) {
            return 0;
        }
        long booked = appointmentRepository.countBySlot_IdAndStatus(slot.getId(), AppointmentStatus.BOOKED);
        return Math.max(0, slot.getCapacity() - (int) booked);
    }

    private Duration holdTtl() {
        Duration ttl = redisProperties.getSlotHoldTtl();
        return ttl == null || ttl.isZero() || ttl.isNegative() ? Duration.ofMinutes(2) : ttl;
    }

    private static Map<String, Object> appointmentPayload(CaseAppointment appointment) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("appointmentId", appointment.getId().toString());
        payload.put("caseId", appointment.getCaseFile().getId().toString());
        payload.put("slotId", appointment.getSlot().getId().toString());
        payload.put("status", appointment.getStatus().name());
        payload.put("startsAt", appointment.getSlot().getStartsAt().toString());
        payload.put("endsAt", appointment.getSlot().getEndsAt().toString());
        return payload;
    }

    private static UUID requireOrganization() {
        UUID organizationId = TenantContext.getOrganizationId();
        if (organizationId == null) {
            throw BusinessException.forbidden("error.organization.missing");
        }
        return organizationId;
    }
}
