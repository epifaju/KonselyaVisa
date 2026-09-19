package com.konselyavisa.catalog.api;

import com.konselyavisa.catalog.domain.Country;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CountryMapper {

    CountryResponse toResponse(Country country);
}
