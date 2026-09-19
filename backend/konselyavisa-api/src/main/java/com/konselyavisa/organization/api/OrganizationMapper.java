package com.konselyavisa.organization.api;

import com.konselyavisa.organization.domain.Organization;
import java.util.Map;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrganizationMapper {

    @Mapping(target = "settings", source = "settings.settings")
    OrganizationResponse toResponse(Organization organization);

    default Map<String, Object> mapSettings(Map<String, Object> settings) {
        return settings;
    }
}
