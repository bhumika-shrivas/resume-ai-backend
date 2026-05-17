package com.resumeai.section.security;

/**
 * JWT filtering not required in section-service.
 * The API Gateway validates all tokens before forwarding requests.
 * User identity is available via the X-Auth-User request header.
 */
public class JwtFilter {
}