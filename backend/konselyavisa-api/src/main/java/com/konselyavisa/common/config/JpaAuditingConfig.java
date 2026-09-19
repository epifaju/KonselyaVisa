package com.konselyavisa.common.config;

import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {

    @Bean
    AuditorAware<String> auditorAware() {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return Optional.empty();
            }
            Object principal = authentication.getPrincipal();
            if (principal instanceof Jwt jwt) {
                String subject = jwt.getClaimAsString("preferred_username");
                if (subject == null || subject.isBlank()) {
                    subject = jwt.getSubject();
                }
                return Optional.ofNullable(subject);
            }
            String name = authentication.getName();
            if (name == null || "anonymousUser".equals(name)) {
                return Optional.empty();
            }
            return Optional.of(name);
        };
    }
}
