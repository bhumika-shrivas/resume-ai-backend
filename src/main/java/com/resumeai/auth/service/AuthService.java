package com.resumeai.auth.service;

import com.resumeai.auth.entity.User;
import com.resumeai.auth.dto.JwtResponse;
import com.resumeai.auth.dto.UpdateProfileRequest;
import com.resumeai.auth.dto.ChangePasswordRequest;
import com.resumeai.auth.dto.TokenRefreshRequest;

public interface AuthService {
    User register(User user);
    JwtResponse login(String email, String password);
    void logout(String token);
    boolean validateToken(String token);
    JwtResponse refreshToken(TokenRefreshRequest request);
    User getUserById(Long userId);
    User getUserByEmail(String email);
    User updateProfile(Long userId, UpdateProfileRequest request);
    void changePassword(Long userId, ChangePasswordRequest request);
    void updateSubscription(Long userId, String plan);
    void deactivateAccount(Long userId);
    JwtResponse oauth2Login(String email, String name, String provider);
    java.util.List<com.resumeai.auth.entity.AuditLog> getAuditLogs();
    void sendPasswordResetOtp(String email);
    void resetPasswordWithOtp(String email, String otp, String newPassword);
}
