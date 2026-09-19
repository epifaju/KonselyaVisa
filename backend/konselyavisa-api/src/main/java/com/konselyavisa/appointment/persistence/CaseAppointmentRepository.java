package com.konselyavisa.appointment.persistence;

import com.konselyavisa.appointment.domain.AppointmentStatus;
import com.konselyavisa.appointment.domain.CaseAppointment;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseAppointmentRepository extends JpaRepository<CaseAppointment, UUID> {

    boolean existsByCaseFile_IdAndStatus(UUID caseId, AppointmentStatus status);

    @EntityGraph(attributePaths = {"caseFile"})
    List<CaseAppointment> findByCaseFile_IdIn(Collection<UUID> caseIds);

    long countBySlot_IdAndStatus(UUID slotId, AppointmentStatus status);

    @EntityGraph(attributePaths = {"slot", "caseFile"})
    List<CaseAppointment> findByCaseFile_IdOrderByCreatedAtDesc(UUID caseId);

    @EntityGraph(attributePaths = {"slot", "caseFile"})
    Optional<CaseAppointment> findDetailedById(UUID id);

    @EntityGraph(attributePaths = {"slot", "caseFile"})
    List<CaseAppointment> findBySlot_IdAndStatus(UUID slotId, AppointmentStatus status);
}
