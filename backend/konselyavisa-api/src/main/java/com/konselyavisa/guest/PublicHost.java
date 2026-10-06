package com.konselyavisa.guest;

import com.konselyavisa.organization.OrganizationBrandingSettings;

public final class PublicHost {

    private PublicHost() {}

    public static String domainHint(String domainParam, String hostHeader) {
        String fromParam = OrganizationBrandingSettings.normalizeDomainHint(domainParam);
        if (fromParam != null) {
            return fromParam;
        }
        return OrganizationBrandingSettings.normalizeDomainHint(hostHeader);
    }
}
