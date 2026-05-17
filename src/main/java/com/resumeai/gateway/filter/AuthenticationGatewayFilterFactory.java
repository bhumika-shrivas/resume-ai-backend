package com.resumeai.gateway.filter;

import com.resumeai.gateway.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.util.List;

@Component
public class AuthenticationGatewayFilterFactory extends AbstractGatewayFilterFactory<AuthenticationGatewayFilterFactory.Config> {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationGatewayFilterFactory.class);

    // RouteValidator checks if the current request path requires authentication (e.g., skips login/register paths)
    @Autowired
    private RouteValidator validator;

    // JwtUtil contains logic to decode and verify JSON Web Tokens
    @Autowired
    private JwtUtil jwtUtil;

    public AuthenticationGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return ((exchange, chain) -> {
            // Check if the incoming request targets a secured route
            if (validator.isSecured.test(exchange.getRequest())) {
                logger.debug("Secured route accessed: {}", exchange.getRequest().getURI().getPath());
                
                // Reject the request with 401 Unauthorized if there is no Authorization header at all
                if (!exchange.getRequest().getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                    logger.warn("Missing Authorization header for path: {}", exchange.getRequest().getURI().getPath());
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                }

                // Extract the Authorization header values; reject if empty
                List<String> authHeaders = exchange.getRequest().getHeaders().get(HttpHeaders.AUTHORIZATION);
                if (authHeaders == null || authHeaders.isEmpty()) {
                    logger.warn("Empty Authorization header for path: {}", exchange.getRequest().getURI().getPath());
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                }

                // Get the first Authorization header and remove the "Bearer " prefix to isolate the raw JWT string
                String authHeader = authHeaders.get(0);
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    authHeader = authHeader.substring(7);
                }
                
                try {
                    // Cryptographically verify the token's signature and expiration
                    jwtUtil.validateToken(authHeader);
                    
                    // Extract the subject (which is the user's email) and role from the token
                    Claims claims = jwtUtil.getClaims(authHeader);
                    String email = claims.getSubject();
                    String role = claims.get("role", String.class);
                    String plan = claims.get("plan", String.class);
                    
                    logger.debug("Token validated for user: {}, role: {}, plan: {}", email, role, plan);
                    
                    // Mutate the original request to inject custom headers before passing it downstream.
                    ServerWebExchange modifiedExchange = exchange.mutate()
                            .request(exchange.getRequest().mutate()
                                    .header("X-Auth-User", email)
                                    .header("X-Auth-Role", role != null ? role : "USER")
                                    .header("X-Auth-Plan", plan != null ? plan : "FREE")
                                    .header("loggedInUser", email) 
                                    .build())
                            .build();
                    
                    // Pass the newly modified request to the next filter/downstream microservice
                    return chain.filter(modifiedExchange);
                } catch (Exception e) {
                    // Token was invalid, expired, or tampered with - reject the request
                    logger.error("Invalid token attempt for path {}: {}", exchange.getRequest().getURI().getPath(), e.getMessage());
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                }
            }
            // If the route was NOT secured (e.g., login route), just pass the request through without changes
            return chain.filter(exchange);
        });
    }

    public static class Config {
    }
}