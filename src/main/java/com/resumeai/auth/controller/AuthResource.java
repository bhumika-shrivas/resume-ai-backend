package com.resumeai.auth.controller;

import com.resumeai.auth.dto.ChangePasswordRequest;
import com.resumeai.auth.dto.JwtResponse;
import com.resumeai.auth.dto.LoginRequest;
import com.resumeai.auth.dto.RegisterRequest;
import com.resumeai.auth.dto.TokenRefreshRequest;
import com.resumeai.auth.dto.PasswordResetOtpRequest;
import com.resumeai.auth.dto.PasswordResetRequest;
import com.resumeai.auth.dto.UpdateProfileRequest;
import com.resumeai.auth.dto.UserDTO;
import com.resumeai.auth.entity.User;
import com.resumeai.auth.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/auth")
public class AuthResource {

    private static final Logger logger = LoggerFactory.getLogger(AuthResource.class);

    @Autowired
    private AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<User> register(@RequestBody RegisterRequest request) {
        User user = new User();
        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setPasswordHash(request.getPasswordHash());
        return ResponseEntity.ok(authService.register(user));
    }

    @PostMapping("/login")
    public ResponseEntity<JwtResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request.getEmail(), request.getPassword()));
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(@RequestHeader("Authorization") String token) {
        authService.logout(token);
        return ResponseEntity.ok("Logged out successfully");
    }

    @PostMapping("/refresh")
    public ResponseEntity<JwtResponse> refresh(@RequestBody TokenRefreshRequest request) {
        return ResponseEntity.ok(authService.refreshToken(request));
    }

    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(@RequestHeader(value = "X-Auth-User", required = false) String email,
                                           @RequestHeader(value = "loggedInUser", required = false) String fallbackEmail) {
        String targetEmail = email != null ? email : fallbackEmail;
        if (targetEmail == null) {
            return ResponseEntity.status(401).body("No user session found");
        }
        User user = authService.getUserByEmail(targetEmail);
        return ResponseEntity.ok(convertToDTO(user));
    }

    @GetMapping("/users/email/{email:.+}")
    public ResponseEntity<UserDTO> getUserByEmail(@PathVariable String email) {
        User user = authService.getUserByEmail(email);
        return ResponseEntity.ok(convertToDTO(user));
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestHeader(value = "X-Auth-User", required = false) String email,
                                              @RequestHeader(value = "loggedInUser", required = false) String fallbackEmail,
                                              @RequestBody UpdateProfileRequest request) {
        String targetEmail = email != null ? email : fallbackEmail;
        
        if (targetEmail == null) {
            logger.error("Profile update failed: No user email found in headers");
            return ResponseEntity.status(401).body("Unauthorized: No user session found");
        }

        logger.info("Received update profile request for user: {}", targetEmail);
        try {
            User currentUser = authService.getUserByEmail(targetEmail);
            User updatedUser = authService.updateProfile(currentUser.getId(), request);
            logger.info("Profile updated successfully for user: {}", targetEmail);
            return ResponseEntity.ok(convertToDTO(updatedUser));
        } catch (Exception e) {
            logger.error("Error updating profile for user {}: {}", targetEmail, e.getMessage());
            return ResponseEntity.status(500).body("Error updating profile: " + e.getMessage());
        }
    }

    private UserDTO convertToDTO(User user) {
        return new UserDTO(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                user.getSubscriptionPlan(),
                user.getHeadline(),
                user.getAbout(),
                user.getProvider(),
                user.isActive()
        );
    }

    @PutMapping("/password")
    public ResponseEntity<String> changePassword(@RequestHeader("X-Auth-User") String email, 
                                                 @RequestBody ChangePasswordRequest request) {
        User currentUser = authService.getUserByEmail(email);
        authService.changePassword(currentUser.getId(), request);
        return ResponseEntity.ok("Password changed successfully");
    }

    @PutMapping("/subscription")
    public ResponseEntity<String> updateSubscription(@RequestHeader("X-Auth-User") String email, 
                                                     @RequestParam String plan) {
        User currentUser = authService.getUserByEmail(email);
        authService.updateSubscription(currentUser.getId(), plan);
        return ResponseEntity.ok("Subscription updated successfully");
    }

    @PostMapping("/deactivate")
    public ResponseEntity<String> deactivateAccount(@RequestHeader("X-Auth-User") String email) {
        User currentUser = authService.getUserByEmail(email);
        authService.deactivateAccount(currentUser.getId());
        return ResponseEntity.ok("Account deactivated successfully");
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<java.util.List<com.resumeai.auth.entity.AuditLog>> getAuditLogs() {
        return ResponseEntity.ok(authService.getAuditLogs());
    }

    @GetMapping("/test-oauth")
    public ResponseEntity<?> testOauth() {
        try {
            return ResponseEntity.ok(authService.oauth2Login("test.user@gmail.com", "Test User", "GOOGLE"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }
    @PutMapping("/internal/upgrade/{email:.+}")
    public ResponseEntity<Void> upgradeUserToPremium(@PathVariable String email) {
        logger.info("Internal request to upgrade user to premium: {}", email);
        User user = authService.getUserByEmail(email);
        authService.updateSubscription(user.getId(), "PREMIUM");
        return ResponseEntity.ok().build();
    }

    @PostMapping("/password-reset/send-otp")
    public ResponseEntity<?> sendPasswordResetOtp(@RequestBody PasswordResetOtpRequest request) {
        try {
            authService.sendPasswordResetOtp(request.getEmail());
            return ResponseEntity.ok(java.util.Map.of("message", "OTP sent successfully to your email"));
        } catch (Exception e) {
            logger.error("Failed to send OTP for {}: {}", request.getEmail(), e.getMessage());
            return ResponseEntity.badRequest().body(java.util.Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/password-reset/verify")
    public ResponseEntity<?> resetPasswordWithOtp(@RequestBody PasswordResetRequest request) {
        try {
            authService.resetPasswordWithOtp(request.getEmail(), request.getOtp(), request.getNewPassword());
            return ResponseEntity.ok(java.util.Map.of("message", "Password reset successfully"));
        } catch (Exception e) {
            logger.error("Password reset failed for {}: {}", request.getEmail(), e.getMessage());
            return ResponseEntity.badRequest().body(java.util.Map.of("message", e.getMessage()));
        }
    }
}