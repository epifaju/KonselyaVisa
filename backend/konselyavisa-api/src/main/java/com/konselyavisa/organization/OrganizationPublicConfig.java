package com.konselyavisa.organization;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(KonselyaPublicProperties.class)
public class OrganizationPublicConfig {}
