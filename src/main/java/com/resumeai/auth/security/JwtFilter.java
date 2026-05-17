package com.resumeai.auth.security;

import com.resumeai.auth.service.RedisService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
public class JwtFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private RedisService redisService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        
        // Skip filter for public paths
        if (path.startsWith("/auth/login") || path.startsWith("/auth/register") || 
            path.startsWith("/auth/refresh") || path.startsWith("/oauth2") || path.startsWith("/login/oauth2")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String authHeader = request.getHeader("Authorization");
        String username = null;
        String jwt = null;

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            jwt = authHeader.substring(7);
            
            // Check if token is blacklisted in Redis
            if (redisService.isTokenBlacklisted(jwt)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }

            try {
                username = jwtUtil.extractUsername(jwt);
            } catch (Exception e) {
                // Token is invalid
            }
        } else {
            // API Gateway passes the user email in X-Auth-User header
            username = request.getHeader("X-Auth-User");
        }

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            String role = request.getHeader("X-Auth-Role");
            if (role == null) {
                role = "USER";
                if (jwt != null && jwtUtil.validateToken(jwt)) {
                    role = jwtUtil.extractRole(jwt);
                }
            }

            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                    username, null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role)));
            authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authenticationToken);
        }

        filterChain.doFilter(request, response);
    }
}
