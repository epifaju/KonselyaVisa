package com.konselyavisa.appointment.api;

import com.konselyavisa.appointment.domain.AppointmentSlot;
import com.konselyavisa.appointment.domain.CaseAppointment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AppointmentMapper {

    @Mapping(target = "remainingCapacity", source = "remainingCapacity")
    AppointmentSlotResponse toSlotResponse(AppointmentSlot slot, int remainingCapacity);

    @Mapping(target = "caseId", source = "appointment.caseFile.id")
    @Mapping(target = "slot", expression = "java(toSlotResponse(appointment.getSlot(), remainingCapacity))")
    AppointmentResponse toResponse(CaseAppointment appointment, int remainingCapacity);
}
