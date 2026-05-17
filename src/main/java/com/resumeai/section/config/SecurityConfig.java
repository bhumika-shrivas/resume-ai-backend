package com.resumeai.section.config;

/**
 * No security config needed — authentication is handled by the API Gateway.
 * The gateway validates the JWT and forwards X-Auth-User header to this service.
 */
public class SecurityConfig {
    // Intentionally empty — Spring Boot auto-configuration handles defaults.
    // Spring Security is NOT on the classpath; auth is delegated to the gateway.
}