package com.smarthealthdog.backend.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enabled ONLY by respiration.inference.enabled=true with the dev profile.
 * Keeps the existing Quartz configuration untouched.
 */
@Configuration
@Profile("dev")
@ConditionalOnProperty(prefix = "respiration.inference", name = "enabled", havingValue = "true")
@EnableScheduling
public class RespirationInferenceSchedulingConfig {
}
