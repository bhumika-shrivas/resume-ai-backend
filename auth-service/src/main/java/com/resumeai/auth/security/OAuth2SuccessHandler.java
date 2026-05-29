package com.resumeai.auth.security;

import com.resumeai.auth.dto.JwtResponse;
import com.resumeai.auth.service.AuthService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    @Autowired
    private AuthService authService;

    // Set FRONTEND_URL env var in your .env file to your Vercel URL
    // Defaults to localhost for local dev
    @Value("${FRONTEND_URL:http://localhost:4200}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        try {
            OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
            
            String email = oAuth2User.getAttribute("email");
            String name = oAuth2User.getAttribute("name");
            
            if (email == null || email.isEmpty()) {
                throw new RuntimeException("Email not provided by OAuth2 provider");
            }

            JwtResponse jwtResponse = authService.oauth2Login(email, name, "GOOGLE");
            
            String targetUrl = UriComponentsBuilder.fromUriString(frontendUrl + "/oauth-callback")
                    .queryParam("token", jwtResponse.getAccessToken())
                    .queryParam("refreshToken", jwtResponse.getRefreshToken())
                    .build().toUriString();
                    
            getRedirectStrategy().sendRedirect(request, response, targetUrl);
        } catch (Exception e) {
            System.err.println("OAuth2 Login Error: " + e.getMessage());
            e.printStackTrace();

            String targetUrl = UriComponentsBuilder.fromUriString(frontendUrl + "/login")
                    .queryParam("error", "OAuth2 login failed")
                    .build().toUriString();
            getRedirectStrategy().sendRedirect(request, response, targetUrl);
        }
    }
}
