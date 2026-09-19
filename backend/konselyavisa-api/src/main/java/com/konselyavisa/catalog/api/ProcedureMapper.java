package com.konselyavisa.catalog.api;

import com.konselyavisa.catalog.domain.ProcedureDefinition;
import com.konselyavisa.catalog.domain.ProcedureVersion;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {CountryMapper.class, ProcedureVersionMapper.class})
public interface ProcedureMapper {

    @Mapping(target = "publishedVersionNumber", expression = "java(publishedVersionNumber)")
    @Mapping(target = "versions", source = "versions")
    ProcedureResponse toResponse(
            ProcedureDefinition definition, Integer publishedVersionNumber, List<ProcedureVersion> versions);
}
