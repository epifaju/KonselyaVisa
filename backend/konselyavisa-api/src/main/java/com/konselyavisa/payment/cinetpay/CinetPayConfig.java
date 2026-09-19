package com.konselyavisa.payment.cinetpay;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(CinetPayProperties.class)
public class CinetPayConfig {

    @Bean
    RestClient cinetPayRestClient(CinetPayProperties properties) {
        return RestClient.builder().baseUrl(properties.getApiBaseUrl()).build();
    }
}
