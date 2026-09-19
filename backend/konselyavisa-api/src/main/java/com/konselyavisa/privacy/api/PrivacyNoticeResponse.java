package com.konselyavisa.privacy.api;

import java.util.Map;

public record PrivacyNoticeResponse(String version, Map<String, String> textI18n) {}
