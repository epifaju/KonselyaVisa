package com.konselyavisa.tenancy;

import jakarta.persistence.EntityManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import org.hibernate.Filter;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TenantSessionBinder {

    private static final Logger log = LoggerFactory.getLogger(TenantSessionBinder.class);

    private TenantSessionBinder() {}

    public static void bind(EntityManager entityManager) {
        UUID organizationId = TenantContext.getOrganizationId();
        Session session = entityManager.unwrap(Session.class);
        applyHibernateFilter(session, organizationId);
        session.doWork(connection -> applyRls(connection, organizationId, TenantContext.isPlatformAdmin()));
    }

    public static void applyRls(Connection connection) throws SQLException {
        applyRls(connection, TenantContext.getOrganizationId(), TenantContext.isPlatformAdmin());
    }

    public static void applyRls(Connection connection, UUID organizationId, boolean platformAdmin) throws SQLException {
        try (PreparedStatement adminStatement =
                connection.prepareStatement("SELECT set_config('app.is_platform_admin', ?, true)")) {
            adminStatement.setString(1, Boolean.toString(platformAdmin));
            adminStatement.execute();
        }
        try (PreparedStatement orgStatement =
                connection.prepareStatement("SELECT set_config('app.current_org', ?, true)")) {
            orgStatement.setString(1, organizationId == null ? "" : organizationId.toString());
            orgStatement.execute();
        }
    }

    private static void applyHibernateFilter(Session session, UUID organizationId) {
        try {
            if (organizationId == null || TenantContext.isPlatformAdmin()) {
                Filter enabled = session.getEnabledFilter(TenantFilters.FILTER_NAME);
                if (enabled != null) {
                    session.disableFilter(TenantFilters.FILTER_NAME);
                }
                return;
            }
            session.enableFilter(TenantFilters.FILTER_NAME)
                    .setParameter(TenantFilters.PARAM_NAME, organizationId);
        } catch (HibernateException ex) {
            log.debug(
                    "Hibernate tenant filter '{}' is not registered yet (no tenant entity loaded): {}",
                    TenantFilters.FILTER_NAME,
                    ex.getMessage());
        }
    }
}
