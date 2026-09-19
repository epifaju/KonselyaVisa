package com.konselyavisa.redis;

import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.tenancy.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

public class RedisRateLimitFilter extends OncePerRequestFilter {

    private final RedisRateLimiter rateLimiter;
    private final HandlerExceptionResolver exceptionResolver;

    public RedisRateLimitFilter(RedisRateLimiter rateLimiter, HandlerExceptionResolver exceptionResolver) {
        this.rateLimiter = rateLimiter;
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.startsWith("/actuator") || path.startsWith("/api/v1/payments/webhooks")) {
            return true;
        }
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            rateLimiter.check(bucket(request), isWrite(request.getMethod()));
            filterChain.doFilter(request, response);
        } catch (BusinessException exception) {
            exceptionResolver.resolveException(request, response, null, exception);
        }
    }

    private static boolean isWrite(String method) {
        return "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method);
    }

    private static String bucket(HttpServletRequest request) {
        UUID organizationId = TenantContext.getOrganizationId();
        if (organizationId != null) {
            return "org:" + organizationId;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return "ip:" + forwarded.split(",")[0].trim();
        }
        return "ip:" + request.getRemoteAddr();
    }
}
