package com.konselyavisa.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.konselyavisa.dossier.CaseFile;
import com.konselyavisa.dossier.CaseService;
import com.konselyavisa.dossier.CaseStatus;
import com.konselyavisa.outbox.OutboxAppender;
import com.konselyavisa.redis.KonselyaRedisProperties;
import com.konselyavisa.redis.SlotHoldStore;
import com.konselyavisa.tenancy.TenantContext;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    @Mock
    private CaseService caseService;

    @Mock
    private AppointmentSlotRepository slotRepository;

    @Mock
    private CaseAppointmentRepository appointmentRepository;

    @Mock
    private OutboxAppender outboxAppender;

    @Mock
    private AppointmentMapper appointmentMapper;

    @Mock
    private SlotHoldStore slotHoldStore;

    private KonselyaRedisProperties redisProperties;
    private AppointmentService service;
    private UUID orgId;
    private UUID caseId;
    private UUID slotId;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
        caseId = UUID.randomUUID();
        slotId = UUID.randomUUID();
        redisProperties = new KonselyaRedisProperties();
        redisProperties.setSlotHoldTtl(Duration.ofMinutes(2));
        service = new AppointmentService(
                caseService,
                slotRepository,
                appointmentRepository,
                outboxAppender,
                appointmentMapper,
                slotHoldStore,
                redisProperties);
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createSlotRequiresOrganization() {
        CreateSlotRequest request = new CreateSlotRequest(
                Instant.now().plusSeconds(3600),
                Instant.now().plusSeconds(7200),
                2,
                Map.of("fr", "Consulat"));

        assertThatThrownBy(() -> service.createSlot(request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.organization.missing");
    }

    @Test
    void createSlotRejectsInvalidWindow() {
        TenantContext.setOrganizationId(orgId);
        Instant start = Instant.now().plusSeconds(3600);
        CreateSlotRequest request = new CreateSlotRequest(start, start, 2, Map.of("fr", "Consulat"));

        assertThatThrownBy(() -> service.createSlot(request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.appointment.window_invalid");
    }

    @Test
    void holdRejectsPastSlot() {
        TenantContext.setOrganizationId(orgId);
        CaseFile caseFile = openCase();
        when(caseService.requireAccessible(caseId)).thenReturn(caseFile);
        when(appointmentRepository.existsByCaseFile_IdAndStatus(caseId, AppointmentStatus.BOOKED))
                .thenReturn(false);
        AppointmentSlot slot = openSlot();
        slot.setStartsAt(Instant.now().minusSeconds(60));
        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));

        assertThatThrownBy(() -> service.hold(caseId, new HoldAppointmentRequest(slotId)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.appointment.slot_past");
        verify(slotHoldStore, never()).tryAcquire(any(), any(), any(), anyInt(), any());
    }

    @Test
    void holdRejectsFullSlotWithoutRedis() {
        TenantContext.setOrganizationId(orgId);
        CaseFile caseFile = openCase();
        when(caseService.requireAccessible(caseId)).thenReturn(caseFile);
        when(appointmentRepository.existsByCaseFile_IdAndStatus(caseId, AppointmentStatus.BOOKED))
                .thenReturn(false);
        AppointmentSlot slot = openSlot();
        slot.setCapacity(1);
        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(appointmentRepository.countBySlot_IdAndStatus(slotId, AppointmentStatus.BOOKED)).thenReturn(1L);

        assertThatThrownBy(() -> service.hold(caseId, new HoldAppointmentRequest(slotId)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.appointment.slot_full");
        verify(slotHoldStore, never()).tryAcquire(any(), any(), any(), anyInt(), any());
    }

    @Test
    void holdRejectsWhenRedisAcquireFails() {
        TenantContext.setOrganizationId(orgId);
        CaseFile caseFile = openCase();
        when(caseService.requireAccessible(caseId)).thenReturn(caseFile);
        when(appointmentRepository.existsByCaseFile_IdAndStatus(caseId, AppointmentStatus.BOOKED))
                .thenReturn(false);
        AppointmentSlot slot = openSlot();
        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(appointmentRepository.countBySlot_IdAndStatus(slotId, AppointmentStatus.BOOKED)).thenReturn(0L);
        when(slotHoldStore.tryAcquire(eq(orgId), eq(slotId), eq(caseId), eq(2), any(Duration.class)))
                .thenReturn(false);

        assertThatThrownBy(() -> service.hold(caseId, new HoldAppointmentRequest(slotId)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.appointment.hold_unavailable");
    }

    @Test
    void bookRejectsWhenAlreadyBooked() {
        TenantContext.setOrganizationId(orgId);
        CaseFile caseFile = openCase();
        when(caseService.requireAccessible(caseId)).thenReturn(caseFile);
        when(appointmentRepository.existsByCaseFile_IdAndStatus(caseId, AppointmentStatus.BOOKED))
                .thenReturn(true);

        assertThatThrownBy(() -> service.book(caseId, new BookAppointmentRequest(slotId)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.appointment.already_booked");
        verify(slotRepository, never()).lockById(any());
    }

    @Test
    void bookPersistsAppointmentReleasesHoldAndEmitsOutbox() {
        TenantContext.setOrganizationId(orgId);
        CaseFile caseFile = openCase();
        AppointmentSlot slot = openSlot();
        when(caseService.requireAccessible(caseId)).thenReturn(caseFile);
        when(appointmentRepository.existsByCaseFile_IdAndStatus(caseId, AppointmentStatus.BOOKED))
                .thenReturn(false);
        when(slotRepository.lockById(slotId)).thenReturn(Optional.of(slot));
        when(appointmentRepository.countBySlot_IdAndStatus(slotId, AppointmentStatus.BOOKED)).thenReturn(0L);
        when(slotHoldStore.tryAcquire(eq(orgId), eq(slotId), eq(caseId), eq(2), any(Duration.class)))
                .thenReturn(true);
        when(appointmentRepository.saveAndFlush(any(CaseAppointment.class))).thenAnswer(inv -> {
            CaseAppointment appointment = inv.getArgument(0);
            appointment.setId(UUID.randomUUID());
            return appointment;
        });
        when(slotHoldStore.activeCount(orgId, slotId)).thenReturn(0);
        AppointmentResponse mapped = org.mockito.Mockito.mock(AppointmentResponse.class);
        when(appointmentMapper.toResponse(any(CaseAppointment.class), eq(2))).thenReturn(mapped);

        AppointmentResponse response = service.book(caseId, new BookAppointmentRequest(slotId));

        assertThat(response).isSameAs(mapped);
        assertThat(caseFile.getStatus()).isEqualTo(CaseStatus.IN_PROGRESS);
        verify(slotHoldStore).release(orgId, slotId, caseId);
        verify(outboxAppender).appendAppointmentBooked(eq(caseId), any());
    }

    @Test
    void listSlotsMapsOpenSlotsInWindow() {
        TenantContext.setOrganizationId(orgId);
        AppointmentSlot slot = openSlot();
        Instant from = Instant.now();
        Instant to = from.plusSeconds(86_400);
        when(slotRepository.findByStatusAndStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAtAsc(
                        AppointmentSlotStatus.OPEN, from, to))
                .thenReturn(List.of(slot));
        when(appointmentRepository.countBySlot_IdAndStatus(slotId, AppointmentStatus.BOOKED)).thenReturn(0L);
        when(slotHoldStore.activeCount(orgId, slotId)).thenReturn(1);
        AppointmentSlotResponse mapped = org.mockito.Mockito.mock(AppointmentSlotResponse.class);
        when(appointmentMapper.toSlotResponse(slot, 1)).thenReturn(mapped);

        assertThat(service.listSlots(from, to)).containsExactly(mapped);
    }

    @Test
    void listByCaseMapsAppointments() {
        TenantContext.setOrganizationId(orgId);
        CaseFile caseFile = openCase();
        AppointmentSlot slot = openSlot();
        CaseAppointment appointment = bookedAppointment(caseFile, slot);
        when(caseService.requireAccessible(caseId)).thenReturn(caseFile);
        when(appointmentRepository.findByCaseFile_IdOrderByCreatedAtDesc(caseId)).thenReturn(List.of(appointment));
        when(appointmentRepository.countBySlot_IdAndStatus(slotId, AppointmentStatus.BOOKED)).thenReturn(1L);
        when(slotHoldStore.activeCount(orgId, slotId)).thenReturn(0);
        AppointmentResponse mapped = org.mockito.Mockito.mock(AppointmentResponse.class);
        when(appointmentMapper.toResponse(appointment, 1)).thenReturn(mapped);

        assertThat(service.listByCase(caseId)).containsExactly(mapped);
    }

    @Test
    void cancelRejectsUnknownAppointment() {
        UUID appointmentId = UUID.randomUUID();
        when(appointmentRepository.findDetailedById(appointmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(appointmentId))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.appointment.not_found");
    }

    @Test
    void cancelRejectsNonBookedAppointment() {
        CaseFile caseFile = openCase();
        AppointmentSlot slot = openSlot();
        CaseAppointment appointment = bookedAppointment(caseFile, slot);
        appointment.setStatus(AppointmentStatus.CANCELLED);
        when(appointmentRepository.findDetailedById(appointment.getId())).thenReturn(Optional.of(appointment));
        when(caseService.requireAccessible(caseId)).thenReturn(caseFile);

        assertThatThrownBy(() -> service.cancel(appointment.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.appointment.not_cancellable");
    }

    @Test
    void cancelBookedAppointmentEmitsOutbox() {
        TenantContext.setOrganizationId(orgId);
        CaseFile caseFile = openCase();
        AppointmentSlot slot = openSlot();
        CaseAppointment appointment = bookedAppointment(caseFile, slot);
        when(appointmentRepository.findDetailedById(appointment.getId())).thenReturn(Optional.of(appointment));
        when(caseService.requireAccessible(caseId)).thenReturn(caseFile);
        when(appointmentRepository.countBySlot_IdAndStatus(slotId, AppointmentStatus.BOOKED)).thenReturn(0L);
        when(slotHoldStore.activeCount(orgId, slotId)).thenReturn(0);
        AppointmentResponse mapped = org.mockito.Mockito.mock(AppointmentResponse.class);
        when(appointmentMapper.toResponse(appointment, 2)).thenReturn(mapped);

        assertThat(service.cancel(appointment.getId())).isSameAs(mapped);
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        verify(outboxAppender).appendAppointmentCancelled(eq(caseId), any());
    }

    @Test
    void cancelSlotCancelsBookedAppointments() {
        TenantContext.setOrganizationId(orgId);
        AppointmentSlot slot = openSlot();
        CaseFile caseFile = openCase();
        CaseAppointment appointment = bookedAppointment(caseFile, slot);
        when(slotRepository.lockById(slotId)).thenReturn(Optional.of(slot));
        when(appointmentRepository.findBySlot_IdAndStatus(slotId, AppointmentStatus.BOOKED))
                .thenReturn(List.of(appointment));
        AppointmentSlotResponse mapped = org.mockito.Mockito.mock(AppointmentSlotResponse.class);
        when(appointmentMapper.toSlotResponse(slot, 0)).thenReturn(mapped);

        assertThat(service.cancelSlot(slotId)).isSameAs(mapped);
        assertThat(slot.getStatus()).isEqualTo(AppointmentSlotStatus.CANCELLED);
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        verify(outboxAppender).appendAppointmentCancelled(eq(caseId), any());
    }

    @Test
    void cancelSlotIsIdempotentWhenAlreadyCancelled() {
        TenantContext.setOrganizationId(orgId);
        AppointmentSlot slot = openSlot();
        slot.setStatus(AppointmentSlotStatus.CANCELLED);
        when(slotRepository.lockById(slotId)).thenReturn(Optional.of(slot));
        AppointmentSlotResponse mapped = org.mockito.Mockito.mock(AppointmentSlotResponse.class);
        when(appointmentMapper.toSlotResponse(slot, 0)).thenReturn(mapped);

        assertThat(service.cancelSlot(slotId)).isSameAs(mapped);
        verify(appointmentRepository, never()).findBySlot_IdAndStatus(any(), any());
    }

    @Test
    void holdRejectsClosedSlot() {
        TenantContext.setOrganizationId(orgId);
        CaseFile caseFile = openCase();
        when(caseService.requireAccessible(caseId)).thenReturn(caseFile);
        when(appointmentRepository.existsByCaseFile_IdAndStatus(caseId, AppointmentStatus.BOOKED))
                .thenReturn(false);
        AppointmentSlot slot = openSlot();
        slot.setStatus(AppointmentSlotStatus.CANCELLED);
        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));

        assertThatThrownBy(() -> service.hold(caseId, new HoldAppointmentRequest(slotId)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.appointment.slot_closed");
    }

    private CaseAppointment bookedAppointment(CaseFile caseFile, AppointmentSlot slot) {
        CaseAppointment appointment = new CaseAppointment();
        appointment.setId(UUID.randomUUID());
        appointment.setCaseFile(caseFile);
        appointment.setSlot(slot);
        appointment.setStatus(AppointmentStatus.BOOKED);
        return appointment;
    }

    private CaseFile openCase() {
        CaseFile caseFile = new CaseFile();
        caseFile.setId(caseId);
        caseFile.setStatus(CaseStatus.CREATED);
        caseFile.setReference("KV-2026-APT0001");
        return caseFile;
    }

    private AppointmentSlot openSlot() {
        AppointmentSlot slot = new AppointmentSlot();
        slot.setId(slotId);
        slot.setStatus(AppointmentSlotStatus.OPEN);
        slot.setCapacity(2);
        slot.setStartsAt(Instant.now().plusSeconds(3600));
        slot.setEndsAt(Instant.now().plusSeconds(7200));
        slot.setLocationI18n(Map.of("fr", "Consulat"));
        return slot;
    }
}
