package com.konselyavisa.identity;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(KeycloakAdminProperties.class)
public class KeycloakCitizenDirectoryConfig {

    @Bean
    @ConditionalOnProperty(prefix = "konselyavisa.keycloak.admin", name = "enabled", havingValue = "true")
    KeycloakCitizenDirectory httpKeycloakCitizenDirectory(KeycloakAdminProperties properties) {
        return new HttpKeycloakCitizenDirectory(properties);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "konselyavisa.keycloak.admin",
            name = "enabled",
            havingValue = "false",
            matchIfMissing = true)
    KeycloakCitizenDirectory stubKeycloakCitizenDirectory() {
        return new StubKeycloakCitizenDirectory();
    }
}
