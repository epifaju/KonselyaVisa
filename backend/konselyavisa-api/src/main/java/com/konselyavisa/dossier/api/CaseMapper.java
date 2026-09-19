package com.konselyavisa.dossier.api;

import com.konselyavisa.applicant.Applicant;
import com.konselyavisa.catalog.api.CountryMapper;
import com.konselyavisa.dossier.CaseFile;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = CountryMapper.class)
public interface CaseMapper {

    ApplicantResponse toApplicantResponse(Applicant applicant);

    @Mapping(target = "procedureDefinitionId", source = "procedureDefinition.id")
    @Mapping(target = "procedureCode", source = "procedureDefinition.code")
    @Mapping(target = "procedureNameI18n", source = "procedureDefinition.nameI18n")
    @Mapping(target = "originCountry", source = "procedureDefinition.originCountry")
    @Mapping(target = "destinationCountry", source = "procedureDefinition.destinationCountry")
    @Mapping(target = "procedureVersionId", source = "procedureVersion.id")
    @Mapping(target = "procedureVersionNumber", source = "procedureVersion.versionNumber")
    @Mapping(target = "estimatedInstructionDays", source = "procedureVersion.estimatedInstructionDays")
    @Mapping(target = "nextAction", ignore = true)
    @Mapping(target = "nextActionMessageKey", ignore = true)
    @Mapping(target = "correctionMessageKey", ignore = true)
    @Mapping(target = "listUrgencyGroup", ignore = true)
    @Mapping(target = "listActionMessageKey", ignore = true)
    @Mapping(target = "listSubtitleMessageKey", ignore = true)
    CaseResponse toResponse(CaseFile caseFile);
}
