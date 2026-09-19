package com.konselyavisa.organization;

import com.konselyavisa.organization.domain.FeatureFlag;
import com.konselyavisa.organization.persistence.FeatureFlagRepository;
import com.konselyavisa.tenancy.TenantContext;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeatureFlagService {

    private final FeatureFlagRepository featureFlagRepository;

    public FeatureFlagService(FeatureFlagRepository featureFlagRepository) {
        this.featureFlagRepository = featureFlagRepository;
    }

    /**
     * Org flag wins over global. Missing flag defaults to {@code defaultIfMissing}
     * so catalog entries without a row (visa seed, ad-hoc tests) stay offered.
     */
    @Transactional(readOnly = true)
    public boolean isEnabled(String flagKey, boolean defaultIfMissing) {
        UUID organizationId = TenantContext.getOrganizationId();
        if (organizationId != null) {
            var orgFlag = featureFlagRepository.findByKeyAndOrganizationId(flagKey, organizationId);
            if (orgFlag.isPresent()) {
                return orgFlag.get().isEnabled();
            }
        }
        return featureFlagRepository
                .findByKeyAndOrganizationIdIsNull(flagKey)
                .map(FeatureFlag::isEnabled)
                .orElse(defaultIfMissing);
    }

    @Transactional(readOnly = true)
    public boolean isProcedureOffered(String procedureCode) {
        return isEnabled(FeatureFlagKeys.procedure(procedureCode), true);
    }

    /**
     * Missing provider flags default to on so MOCK/MANUAL keep working without a seed
     * row. Seed {@code payment.provider.STRIPE} (or later CinetPay) to roll out by org.
     */
    @Transactional(readOnly = true)
    public boolean isPaymentProviderEnabled(String providerCode) {
        return isEnabled(FeatureFlagKeys.paymentProvider(providerCode), true);
    }
}
