package com.konselyavisa.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.tenancy.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.HandlerExceptionResolver;

@ExtendWith(MockitoExtension.class)
class RedisRateLimitFilterTest {

    @Mock
    private RedisRateLimiter rateLimiter;

    @Mock
    private HandlerExceptionResolver exceptionResolver;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private RedisRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RedisRateLimitFilter(rateLimiter, exceptionResolver);
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldNotFilterSkipsActuatorPaymentWebhooksAndOptions() {
        when(request.getRequestURI()).thenReturn("/actuator/health");
        assertThat(filter.shouldNotFilter(request)).isTrue();

        when(request.getRequestURI()).thenReturn("/api/v1/payments/webhooks/stripe");
        assertThat(filter.shouldNotFilter(request)).isTrue();

        when(request.getRequestURI()).thenReturn("/api/v1/cases");
        when(request.getMethod()).thenReturn("OPTIONS");
        assertThat(filter.shouldNotFilter(request)).isTrue();

        when(request.getMethod()).thenReturn("GET");
        assertThat(filter.shouldNotFilter(request)).isFalse();
    }

    @Test
    void doFilterInternalUsesOrgBucketWhenTenantContextSet() throws Exception {
        UUID orgId = UUID.randomUUID();
        TenantContext.setOrganizationId(orgId);
        when(request.getMethod()).thenReturn("GET");

        filter.doFilterInternal(request, response, filterChain);

        verify(rateLimiter).check("org:" + orgId, false);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternalUsesForwardedForIpWhenNoOrg() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1, 10.0.0.2");

        filter.doFilterInternal(request, response, filterChain);

        verify(rateLimiter).check("ip:10.0.0.1", true);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternalUsesRemoteAddrWhenNoForwardedHeader() throws Exception {
        when(request.getMethod()).thenReturn("PUT");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        filter.doFilterInternal(request, response, filterChain);

        verify(rateLimiter).check("ip:127.0.0.1", true);
    }

    @Test
    void doFilterInternalDelegatesRateLimitExceptionToResolver() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRemoteAddr()).thenReturn("1.2.3.4");
        BusinessException limited = BusinessException.tooManyRequests("error.rate_limited");
        doThrow(limited).when(rateLimiter).check(any(), anyBoolean());

        filter.doFilterInternal(request, response, filterChain);

        verify(exceptionResolver).resolveException(eq(request), eq(response), isNull(), eq(limited));
        verify(filterChain, never()).doFilter(any(), any());
    }
}
