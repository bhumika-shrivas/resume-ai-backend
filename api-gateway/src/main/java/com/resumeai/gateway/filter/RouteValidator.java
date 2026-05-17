package com.resumeai.gateway.filter;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Predicate;

@Component
public class RouteValidator {

    public static final List<String> openApiEndpoints = List.of(
            "/auth/register",
            "/auth/login",
            "/auth/refresh",
            "/auth/oauth2",
            "/oauth2/",
            "/login/oauth2/",
            "/api/v1/templates",
            "/api/v1/resumes/public",
            "/eureka"
    );

    public Predicate<ServerHttpRequest> isSecured =
            request -> {
                // Always allow OPTIONS requests for CORS preflight
                if (request.getMethod().name().equals("OPTIONS")) {
                    return false;
                }
                return openApiEndpoints
                        .stream()
                        .noneMatch(uri -> request.getURI().getPath().startsWith(uri));
            };
}
