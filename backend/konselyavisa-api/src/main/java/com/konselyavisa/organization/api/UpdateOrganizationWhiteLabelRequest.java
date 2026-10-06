package com.konselyavisa.organization.api;

/**
 * White-label / payment settings written into {@code organization_settings.settings}.
 * Null fields are left unchanged; blank {@code brandColor}/{@code domain} clears the key.
 */
public record UpdateOrganizationWhiteLabelRequest(String brandColor, String domain, String paymentProvider) {}
