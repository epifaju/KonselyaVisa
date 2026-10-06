package com.konselyavisa.document.api;

import com.konselyavisa.document.domain.DocumentUploadRefusal;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DocumentUploadRefusalMapper {

    @Mapping(target = "caseId", source = "caseFile.id")
    @Mapping(target = "attemptedAt", source = "createdAt")
    DocumentUploadRefusalResponse toResponse(DocumentUploadRefusal refusal);
}
