package com.konselyavisa.document.api;

import com.konselyavisa.document.domain.CaseDocument;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DocumentMapper {

    @Mapping(target = "caseId", source = "caseFile.id")
    @Mapping(target = "extractedFields", ignore = true)
    DocumentResponse toResponse(CaseDocument document);
}
