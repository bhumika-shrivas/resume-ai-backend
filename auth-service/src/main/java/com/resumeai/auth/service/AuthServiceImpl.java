package com.resumeai.auth.service;

import com.resumeai.auth.entity.User;
import com.resumeai.auth.repository.UserRepository;
import com.resumeai.auth.security.JwtUtil;
import com.resumeai.auth.dto.UpdateProfileRequest;
import com.resumeai.auth.dto.ChangePasswordRequest;
import com.resumeai.auth.dto.JwtResponse;
import com.resumeai.auth.dto.TokenRefreshRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthServiceImpl.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private RedisService redisService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private EmailService emailService;

    private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public User register(User user) {
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new RuntimeException("Email already exists");
        }
        
        user.setActive(true);
        // We expect the raw password to be passed in passwordHash field from DTO mapping
        if (user.getPasswordHash() != null && !user.getPasswordHash().isEmpty()) {
            user.setPasswordHash(passwordEncoder.encode(user.getPasswordHash()));
        }
        user.setRole("USER");
        user.setProvider("LOCAL");
        user.setSubscriptionPlan("FREE");

        User savedUser = userRepository.save(user);
        auditService.log(savedUser.getEmail(), "REGISTER", "User registered via LOCAL provider");
        return savedUser;
    }

    @Override
    public JwtResponse login(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
                
        if (!user.isActive()) {
            throw new RuntimeException("Account is deactivated");
        }

        if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new RuntimeException("Invalid password");
        }

        String accessToken = jwtUtil.generateToken(user.getEmail(), user.getRole(), user.getSubscriptionPlan());
        String refreshToken = jwtUtil.generateRefreshToken(user.getEmail());

        // Store refresh token in Redis mapped to email for 7 days
        redisService.setValue(refreshToken, user.getEmail(), 7, TimeUnit.DAYS);

        auditService.log(user.getEmail(), "LOGIN", "User logged in successfully");

        return JwtResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .role(user.getRole())
                .plan(user.getSubscriptionPlan())
                .build();
    }

    @Override
    public void logout(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        redisService.blacklistToken(token);
    }

    @Override
    public boolean validateToken(String token) {
        return jwtUtil.validateToken(token);
    }

    @Override
    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @Override
    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @Override
    public User updateProfile(Long userId, UpdateProfileRequest request) {
        logger.info("AuthServiceImpl: Updating profile for userId {}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (request.getFullName() != null) user.setFullName(request.getFullName());
        if (request.getPhone() != null) user.setPhone(request.getPhone());
        if (request.getHeadline() != null) user.setHeadline(request.getHeadline());
        if (request.getAbout() != null) user.setAbout(request.getAbout());

        User savedUser = userRepository.save(user);
        logger.info("AuthServiceImpl: Profile updated successfully for userId {}", userId);
        return savedUser;
    }

    @Override
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Old password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    @Override 
    public void updateSubscription(Long userId, String plan) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setSubscriptionPlan(plan);
        userRepository.save(user);
        auditService.log(user.getEmail(), "UPGRADE", "Subscription plan updated to: " + plan);
    }

    @Override
    public void deactivateAccount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setActive(false);
        userRepository.save(user);
    }

    @Override
    public JwtResponse refreshToken(TokenRefreshRequest request) {
        String requestRefreshToken = request.getRefreshToken();
        
        // Check if token exists in Redis
        String email = (String) redisService.getValue(requestRefreshToken);
        if (email == null) {
            throw new RuntimeException("Refresh token is invalid or expired");
        }
        
        if (!jwtUtil.validateToken(requestRefreshToken)) {
            redisService.deleteValue(requestRefreshToken);
            throw new RuntimeException("Refresh token is invalid or expired");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!user.isActive()) {
            throw new RuntimeException("Account is deactivated");
        }

        // Generate new tokens
        String newAccessToken = jwtUtil.generateToken(email, user.getRole(), user.getSubscriptionPlan());
        String newRefreshToken = jwtUtil.generateRefreshToken(email);
        
        // Remove old and add new refresh token
        redisService.deleteValue(requestRefreshToken);
        redisService.setValue(newRefreshToken, email, 7, TimeUnit.DAYS);

        return JwtResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .role(user.getRole())
                .plan(user.getSubscriptionPlan())
                .build();
    }

    @Override
    @Transactional
    public JwtResponse oauth2Login(String email, String name, String provider) {
        User user = userRepository.findByEmail(email).orElseGet(() -> {
            User newUser = new User();
            newUser.setEmail(email);
            newUser.setFullName(name);
            // Assign a random, unguessable password for OAuth users to prevent DB NOT NULL constraint issues
            newUser.setPasswordHash(passwordEncoder.encode(java.util.UUID.randomUUID().toString()));
            newUser.setRole("USER");
            newUser.setProvider(provider != null ? provider.toUpperCase() : "GOOGLE");
            newUser.setSubscriptionPlan("FREE");
            newUser.setActive(true);
            return userRepository.save(newUser);
        });

        if (!user.isActive()) {
            user.setActive(true);
            userRepository.save(user);
        }

        String accessToken = jwtUtil.generateToken(user.getEmail(), user.getRole(), user.getSubscriptionPlan());
        String refreshToken = jwtUtil.generateRefreshToken(user.getEmail());

        redisService.setValue(refreshToken, user.getEmail(), 7, TimeUnit.DAYS);

        return JwtResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .role(user.getRole())
                .plan(user.getSubscriptionPlan())
                .build();
    }

    @Override
    public java.util.List<com.resumeai.auth.entity.AuditLog> getAuditLogs() {
        return auditService.getAllLogs();
    }

    @Override
    public void sendPasswordResetOtp(String email) {
        // Verify user exists
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("No account found with this email"));

        // Generate 6-digit OTP
        String otp = String.format("%06d", new java.security.SecureRandom().nextInt(999999));

        // Store in Redis with 5-minute TTL
        String redisKey = "PWD_OTP_" + email;
        redisService.setValue(redisKey, otp, 5, TimeUnit.MINUTES);

        // Send email
        emailService.sendOtpEmail(email, otp);
        
        auditService.log(email, "PASSWORD_RESET_OTP_SENT", "Password reset OTP sent to email");
        logger.info("Password reset OTP sent for user: {}", email);
    }

    @Override
    public void resetPasswordWithOtp(String email, String otp, String newPassword) {
        // Validate OTP
        String redisKey = "PWD_OTP_" + email;
        String storedOtp = redisService.getValue(redisKey);

        if (storedOtp == null) {
            throw new RuntimeException("OTP has expired. Please request a new one.");
        }

        if (!storedOtp.equals(otp)) {
            throw new RuntimeException("Invalid OTP. Please check and try again.");
        }

        // Update password
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Clean up OTP from Redis
        redisService.deleteValue(redisKey);

        auditService.log(email, "PASSWORD_RESET_SUCCESS", "Password reset successfully via OTP");
        logger.info("Password reset successfully for user: {}", email);
    }
}
