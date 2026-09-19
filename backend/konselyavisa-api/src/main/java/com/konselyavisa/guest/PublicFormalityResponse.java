package com.konselyavisa.guest;

import java.util.Map;

public record PublicFormalityResponse(String category, Map<String, String> nameI18n) {}
