package com.konselyavisa.organization;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "konselyavisa.public")
public class KonselyaPublicProperties {

    /** Absolute API base used in outbox emails (logo URLs), e.g. https://api.example.com */
    private String baseUrl = "http://localhost:18083";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String absoluteUrl(String path) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (path == null || path.isBlank()) {
            return base;
        }
        return path.startsWith("/") ? base + path : base + "/" + path;
    }
}
