package com.konselyavisa.organization.service;

import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.organization.OrganizationBrandingSettings;
import com.konselyavisa.organization.api.OrganizationMapper;
import com.konselyavisa.organization.api.OrganizationResponse;
import com.konselyavisa.organization.api.UpdateOrganizationWhiteLabelRequest;
import com.konselyavisa.organization.domain.Organization;
import com.konselyavisa.organization.persistence.OrganizationRepository;
import com.konselyavisa.payment.provider.PaymentProviderCodes;
import com.konselyavisa.tenancy.TenantContext;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationSettingsWriteService {

    private static final Set<String> PAYMENT_PROVIDERS = Set.of(
            PaymentProviderCodes.MOCK,
            PaymentProviderCodes.MANUAL,
            PaymentProviderCodes.STRIPE,
            PaymentProviderCodes.CINETPAY,
            PaymentProviderCodes.PAYDUNYA);

    private final OrganizationRepository organizationRepository;
    private final OrganizationMapper organizationMapper;

    public OrganizationSettingsWriteService(
            OrganizationRepository organizationRepository, OrganizationMapper organizationMapper) {
        this.organizationRepository = organizationRepository;
        this.organizationMapper = organizationMapper;
    }

    @Transactional
    public OrganizationResponse updateCurrent(UpdateOrganizationWhiteLabelRequest request) {
        UUID organizationId = TenantContext.getOrganizationId();
        if (organizationId == null) {
            throw BusinessException.forbidden("error.organization.missing");
        }
        Organization organization = organizationRepository
                .findById(organizationId)
                .orElseThrow(() -> BusinessException.notFound("error.organization.not_found"));
        Map<String, Object> settings = organization.getSettings().getSettings();
        applyBrandColor(settings, request.brandColor());
        applyDomain(settings, request.domain());
        applyPaymentProvider(settings, request.paymentProvider());
        return organizationMapper.toResponse(organization);
    }

    private static void applyBrandColor(Map<String, Object> settings, String brandColor) {
        if (brandColor == null) {
            return;
        }
        if (brandColor.isBlank()) {
            settings.remove("brandColor");
            return;
        }
        String validated = OrganizationBrandingSettings.brandColor(Map.of("brandColor", brandColor));
        if (validated == null) {
            throw BusinessException.badRequest("error.organization.brand_color_invalid");
        }
        settings.put("brandColor", validated);
    }

    private static void applyDomain(Map<String, Object> settings, String domain) {
        if (domain == null) {
            return;
        }
        if (domain.isBlank()) {
            settings.remove("domain");
            return;
        }
        String validated = OrganizationBrandingSettings.domain(Map.of("domain", domain));
        if (validated == null) {
            throw BusinessException.badRequest("error.organization.domain_invalid");
        }
        settings.put("domain", validated);
    }

    private static void applyPaymentProvider(Map<String, Object> settings, String paymentProvider) {
        if (paymentProvider == null) {
            return;
        }
        String code = paymentProvider.trim().toUpperCase(Locale.ROOT);
        if (!PAYMENT_PROVIDERS.contains(code)) {
            throw BusinessException.badRequest("error.organization.payment_provider_invalid");
        }
        settings.put("paymentProvider", code);
    }
}
