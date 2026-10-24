package com.konselyavisa.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.konselyavisa.document.crypto.ExtractedFieldsKeyHolder;
import jakarta.persistence.EntityManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.UUID;
import org.hibernate.Filter;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.jdbc.Work;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TenantSessionBinderTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private Session session;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement adminStatement;

    @Mock
    private PreparedStatement orgStatement;

    @Mock
    private PreparedStatement cryptoStatement;

    @Mock
    private Filter filter;

    @BeforeEach
    void setUp() throws Exception {
        TenantContext.clear();
        ExtractedFieldsKeyHolder.set("secret-key");
        lenient().when(entityManager.unwrap(Session.class)).thenReturn(session);
        lenient()
                .doAnswer(inv -> {
                    Work work = inv.getArgument(0);
                    work.execute(connection);
                    return null;
                })
                .when(session)
                .doWork(any(Work.class));
        lenient()
                .when(connection.prepareStatement("SELECT set_config('app.is_platform_admin', ?, true)"))
                .thenReturn(adminStatement);
        lenient()
                .when(connection.prepareStatement("SELECT set_config('app.current_org', ?, true)"))
                .thenReturn(orgStatement);
        lenient()
                .when(connection.prepareStatement("SELECT set_config('app.extracted_fields_key', ?, true)"))
                .thenReturn(cryptoStatement);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        ExtractedFieldsKeyHolder.set("");
    }

    @Test
    void bindAppliesRlsAndEnablesFilterForTenant() throws Exception {
        UUID orgId = UUID.randomUUID();
        TenantContext.setOrganizationId(orgId);
        TenantContext.setPlatformAdmin(false);
        when(session.enableFilter(TenantFilters.FILTER_NAME)).thenReturn(filter);

        TenantSessionBinder.bind(entityManager);

        verify(filter).setParameter(TenantFilters.PARAM_NAME, orgId);
        verify(adminStatement).setString(1, "false");
        verify(orgStatement).setString(1, orgId.toString());
        verify(cryptoStatement).setString(1, "secret-key");
        verify(adminStatement).execute();
        verify(orgStatement).execute();
        verify(cryptoStatement).execute();
    }

    @Test
    void bindDisablesFilterForPlatformAdminAndUsesEmptyOrg() throws Exception {
        TenantContext.setPlatformAdmin(true);
        TenantContext.setOrganizationId(null);
        when(session.getEnabledFilter(TenantFilters.FILTER_NAME)).thenReturn(filter);

        TenantSessionBinder.bind(entityManager);

        verify(session).disableFilter(TenantFilters.FILTER_NAME);
        verify(session, never()).enableFilter(anyString());
        verify(adminStatement).setString(1, "true");
        verify(orgStatement).setString(1, "");
    }

    @Test
    void bindIgnoresUnregisteredHibernateFilter() {
        UUID orgId = UUID.randomUUID();
        TenantContext.setOrganizationId(orgId);
        doThrow(new HibernateException("missing filter")).when(session).enableFilter(TenantFilters.FILTER_NAME);

        TenantSessionBinder.bind(entityManager);

        verify(session).enableFilter(TenantFilters.FILTER_NAME);
    }

    @Test
    void applyRlsUsesTenantContextDirectly() throws Exception {
        UUID orgId = UUID.randomUUID();
        TenantContext.setOrganizationId(orgId);
        TenantContext.setPlatformAdmin(false);

        TenantSessionBinder.applyRls(connection);

        ArgumentCaptor<String> orgCaptor = ArgumentCaptor.forClass(String.class);
        verify(orgStatement).setString(eq(1), orgCaptor.capture());
        assertThat(orgCaptor.getValue()).isEqualTo(orgId.toString());
    }
}
