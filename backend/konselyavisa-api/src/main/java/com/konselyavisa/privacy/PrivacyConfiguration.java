package com.konselyavisa.privacy;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(KonselyaPrivacyProperties.class)
public class PrivacyConfiguration {}
