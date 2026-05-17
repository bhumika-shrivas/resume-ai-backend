package com.resumeai.auth.controller;

import com.resumeai.auth.dto.AdminStatsResponse;
import com.resumeai.auth.entity.User;
import com.resumeai.auth.repository.UserRepository;
import com.resumeai.auth.repository.UserUsageRepository;
import com.resumeai.auth.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminResource {

    private static final String SUPER_ADMIN_EMAIL = "bhumikashrivas.work@gmail.com";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserUsageRepository usageRepository;

    @Autowired
    private com.resumeai.auth.repository.AuditLogRepository auditLogRepository;

    @Autowired
    private EmailService emailService;

    @GetMapping("/users")
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userRepository.findAll());
    }

    @GetMapping("/stats")
    public ResponseEntity<AdminStatsResponse> getStats() {
        long totalUsers = userRepository.count();
        long premiumUsers = userRepository.findBySubscriptionPlan("PREMIUM").size();
        Long totalAiCalls = usageRepository.getTotalAiCalls();
        Long totalAtsChecks = usageRepository.getTotalAtsChecks();

        return ResponseEntity.ok(new AdminStatsResponse(
                totalUsers,
                premiumUsers,
                totalAiCalls != null ? totalAiCalls : 0,
                totalAtsChecks != null ? totalAtsChecks : 0
        ));
    }

    @PutMapping("/users/{userId}/plan")
    public ResponseEntity<?> updateUserPlan(@PathVariable Long userId, @RequestParam String plan, @RequestHeader("X-Auth-User") String adminEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Protect super admin
        if (SUPER_ADMIN_EMAIL.equalsIgnoreCase(user.getEmail())) {
            return ResponseEntity.badRequest().body("Cannot modify the super admin account.");
        }

        user.setSubscriptionPlan(plan);
        User saved = userRepository.save(user);
        auditLogRepository.save(com.resumeai.auth.entity.AuditLog.of(adminEmail, "UPDATE_USER_PLAN", "Updated plan for user " + userId + " to " + plan));

        // Send email notification to user about plan change
        emailService.sendPlanChangeEmail(user.getEmail(), plan);

        return ResponseEntity.ok(saved);
    }

    @PutMapping("/users/{userId}/role")
    public ResponseEntity<?> updateUserRole(@PathVariable Long userId, @RequestParam String role, @RequestHeader("X-Auth-User") String adminEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Protect super admin — cannot demote the primary admin
        if (SUPER_ADMIN_EMAIL.equalsIgnoreCase(user.getEmail())) {
            return ResponseEntity.badRequest().body("Cannot modify the super admin account.");
        }

        user.setRole(role);
        User saved = userRepository.save(user);
        auditLogRepository.save(com.resumeai.auth.entity.AuditLog.of(adminEmail, "UPDATE_USER_ROLE", "Updated role for user " + userId + " to " + role));

        // Send email notification to user about role change
        emailService.sendRoleChangeEmail(user.getEmail(), role);

        return ResponseEntity.ok(saved);
    }

    @PostMapping("/users/{userId}/suspend")
    public ResponseEntity<?> suspendUser(@PathVariable Long userId, @RequestHeader("X-Auth-User") String adminEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Protect super admin
        if (SUPER_ADMIN_EMAIL.equalsIgnoreCase(user.getEmail())) {
            return ResponseEntity.badRequest().body("Cannot suspend the super admin account.");
        }

        user.setActive(false);
        User saved = userRepository.save(user);
        auditLogRepository.save(com.resumeai.auth.entity.AuditLog.of(adminEmail, "SUSPEND_USER", "Suspended user " + userId));

        // Send email notification to user about suspension
        emailService.sendAccountStatusEmail(user.getEmail(), true);

        return ResponseEntity.ok(saved);
    }

    @PostMapping("/users/{userId}/activate")
    public ResponseEntity<?> activateUser(@PathVariable Long userId, @RequestHeader("X-Auth-User") String adminEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setActive(true);
        User saved = userRepository.save(user);
        auditLogRepository.save(com.resumeai.auth.entity.AuditLog.of(adminEmail, "ACTIVATE_USER", "Activated user " + userId));

        // Send email notification to user about reactivation
        emailService.sendAccountStatusEmail(user.getEmail(), false);

        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/users/{userId}")
    public ResponseEntity<?> deleteUser(@PathVariable Long userId, @RequestHeader("X-Auth-User") String adminEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Protect super admin
        if (SUPER_ADMIN_EMAIL.equalsIgnoreCase(user.getEmail())) {
            return ResponseEntity.badRequest().body("Cannot delete the super admin account.");
        }

        // Send email notification before deleting
        emailService.sendAccountDeletedEmail(user.getEmail());

        userRepository.delete(user);
        auditLogRepository.save(com.resumeai.auth.entity.AuditLog.of(adminEmail, "DELETE_USER", "Deleted user " + userId + " (" + user.getEmail() + ")"));
        return ResponseEntity.ok().build();
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<List<com.resumeai.auth.entity.AuditLog>> getAuditLogs() {
        return ResponseEntity.ok(auditLogRepository.findAll(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt")));
    }
}
