package com.konselyavisa.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class KeycloakCitizenDirectoryConfigTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(KeycloakCitizenDirectoryConfig.class);

    @Test
    void httpBeanWhenAdminEnabled() {
        contextRunner
                .withPropertyValues(
                        "konselyavisa.keycloak.admin.enabled=true",
                        "konselyavisa.keycloak.admin.base-url=https://keycloak.test")
                .run(context -> assertThat(context.getBean(KeycloakCitizenDirectory.class))
                        .isInstanceOf(HttpKeycloakCitizenDirectory.class));
    }

    @Test
    void stubBeanWhenAdminDisabledOrMissing() {
        contextRunner.run(context -> assertThat(context.getBean(KeycloakCitizenDirectory.class))
                .isInstanceOf(StubKeycloakCitizenDirectory.class));

        contextRunner
                .withPropertyValues("konselyavisa.keycloak.admin.enabled=false")
                .run(context -> assertThat(context.getBean(KeycloakCitizenDirectory.class))
                        .isInstanceOf(StubKeycloakCitizenDirectory.class));
    }
}
