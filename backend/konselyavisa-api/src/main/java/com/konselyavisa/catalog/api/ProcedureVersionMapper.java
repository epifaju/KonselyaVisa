package com.konselyavisa.catalog.api;

import com.konselyavisa.catalog.domain.ProcedureVersion;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProcedureVersionMapper {

    ProcedureVersionResponse toResponse(ProcedureVersion version);
}
