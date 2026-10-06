package com.konselyavisa.payment.paydunya;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(PayDunyaProperties.class)
public class PayDunyaConfig {

    @Bean
    RestClient payDunyaRestClient(PayDunyaProperties properties) {
        return RestClient.builder().baseUrl(properties.getApiBaseUrl()).build();
    }
}
