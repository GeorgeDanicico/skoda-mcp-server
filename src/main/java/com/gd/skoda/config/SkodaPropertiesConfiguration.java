package com.gd.skoda.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers the properties every run mode needs. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SkodaProperties.class)
public class SkodaPropertiesConfiguration {
}
