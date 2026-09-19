package com.konselyavisa.catalog.api;

import java.util.Map;
import java.util.UUID;

public record CountryResponse(UUID id, String isoCode, Map<String, String> nameI18n, boolean active) {}
