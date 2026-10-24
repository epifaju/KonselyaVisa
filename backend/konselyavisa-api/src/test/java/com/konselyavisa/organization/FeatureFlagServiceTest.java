package com.konselyavisa.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.konselyavisa.organization.domain.FeatureFlag;
import com.konselyavisa.organization.persistence.FeatureFlagRepository;
import com.konselyavisa.tenancy.TenantContext;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeatureFlagServiceTest {

    @Mock
    private FeatureFlagRepository featureFlagRepository;

    private FeatureFlagService service;
    private UUID orgId;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
        service = new FeatureFlagService(featureFlagRepository);
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void isEnabledUsesOrgOverrideWhenPresent() {
        TenantContext.setOrganizationId(orgId);
        when(featureFlagRepository.findByKeyAndOrganizationId("feature.x", orgId))
                .thenReturn(Optional.of(flag(false)));

        assertThat(service.isEnabled("feature.x", true)).isFalse();
        verify(featureFlagRepository, never()).findByKeyAndOrganizationIdIsNull(any());
    }

    @Test
    void isEnabledFallsBackToGlobalWhenOrgRowMissing() {
        TenantContext.setOrganizationId(orgId);
        when(featureFlagRepository.findByKeyAndOrganizationId("feature.x", orgId)).thenReturn(Optional.empty());
        when(featureFlagRepository.findByKeyAndOrganizationIdIsNull("feature.x"))
                .thenReturn(Optional.of(flag(true)));

        assertThat(service.isEnabled("feature.x", false)).isTrue();
    }

    @Test
    void isEnabledUsesDefaultWhenNoRows() {
        when(featureFlagRepository.findByKeyAndOrganizationIdIsNull("feature.x")).thenReturn(Optional.empty());

        assertThat(service.isEnabled("feature.x", true)).isTrue();
        assertThat(service.isEnabled("feature.x", false)).isFalse();
    }

    @Test
    void isEnabledSkipsOrgLookupWhenTenantMissing() {
        when(featureFlagRepository.findByKeyAndOrganizationIdIsNull("feature.x"))
                .thenReturn(Optional.of(flag(false)));

        assertThat(service.isEnabled("feature.x", true)).isFalse();
        verify(featureFlagRepository, never()).findByKeyAndOrganizationId(any(), any());
    }

    @Test
    void isProcedureOfferedDefaultsToTrueWhenMissing() {
        when(featureFlagRepository.findByKeyAndOrganizationIdIsNull(FeatureFlagKeys.procedure("VISA_TOURISM")))
                .thenReturn(Optional.empty());

        assertThat(service.isProcedureOffered("VISA_TOURISM")).isTrue();
    }

    @Test
    void isPaymentProviderEnabledNormalizesProviderCode() {
        when(featureFlagRepository.findByKeyAndOrganizationIdIsNull("payment.provider.STRIPE"))
                .thenReturn(Optional.of(flag(false)));

        assertThat(service.isPaymentProviderEnabled("stripe")).isFalse();
        verify(featureFlagRepository).findByKeyAndOrganizationIdIsNull(eq("payment.provider.STRIPE"));
    }

    private static FeatureFlag flag(boolean enabled) {
        FeatureFlag featureFlag = new FeatureFlag();
        featureFlag.setEnabled(enabled);
        return featureFlag;
    }
}
