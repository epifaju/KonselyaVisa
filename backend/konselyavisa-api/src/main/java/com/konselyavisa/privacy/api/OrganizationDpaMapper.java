package com.konselyavisa.privacy.api;

import com.konselyavisa.privacy.domain.OrganizationDpaAgreement;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface OrganizationDpaMapper {

    OrganizationDpaResponse toResponse(OrganizationDpaAgreement agreement);
}
