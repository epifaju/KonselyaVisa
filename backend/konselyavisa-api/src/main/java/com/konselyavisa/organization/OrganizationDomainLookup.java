package com.konselyavisa.organization;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves an organization from {@code settings.domain}. Uses {@code app_resolve_org_id_by_domain}
 * (SECURITY DEFINER) — intentional cross-tenant read for public white-label host matching only.
 */
@Repository
public class OrganizationDomainLookup {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public Optional<UUID> findOrganizationIdByDomain(String domain) {
        String normalized = OrganizationBrandingSettings.normalizeDomainHint(domain);
        if (normalized == null) {
            return Optional.empty();
        }
        List<Object> rows = entityManager
                .createNativeQuery("SELECT app_resolve_org_id_by_domain(:domain)")
                .setParameter("domain", normalized)
                .getResultList();
        if (rows.isEmpty() || rows.getFirst() == null) {
            return Optional.empty();
        }
        Object value = rows.getFirst();
        if (value instanceof UUID uuid) {
            return Optional.of(uuid);
        }
        return Optional.of(UUID.fromString(value.toString()));
    }
}
