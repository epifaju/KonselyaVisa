package com.konselyavisa.catalog.api;

import java.util.Map;

public record UpdateCountryRequest(Map<String, String> nameI18n, Boolean active) {}
