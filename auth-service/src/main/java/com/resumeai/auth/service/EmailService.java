package com.resumeai.auth.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public void sendOtpEmail(String to, String otp) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject("ResumeAI — Password Reset OTP");

            String htmlContent = """
                <div style="font-family:'Segoe UI',Arial,sans-serif;max-width:480px;margin:0 auto;padding:24px;background:#f8fafc;border-radius:12px;">
                  <div style="text-align:center;margin-bottom:20px;">
                    <h2 style="color:#1e293b;margin:0;">Resume<span style="color:#2563eb;">AI</span></h2>
                  </div>
                  <div style="background:#ffffff;border-radius:8px;padding:24px;box-shadow:0 1px 3px rgba(0,0,0,0.1);">
                    <h3 style="color:#1e293b;margin:0 0 8px;">Password Reset Request</h3>
                    <p style="color:#64748b;font-size:14px;line-height:1.6;margin:0 0 20px;">
                      Use the following OTP to reset your password. This code is valid for <strong>5 minutes</strong>.
                    </p>
                    <div style="text-align:center;margin:20px 0;">
                      <span style="display:inline-block;background:#1e293b;color:#ffffff;font-size:28px;letter-spacing:8px;padding:14px 28px;border-radius:8px;font-weight:700;">
                        %s
                      </span>
                    </div>
                    <p style="color:#94a3b8;font-size:13px;text-align:center;margin:16px 0 0;">
                      If you didn't request this, you can safely ignore this email.
                    </p>
                  </div>
                  <p style="color:#94a3b8;font-size:12px;text-align:center;margin-top:16px;">
                    &copy; 2026 ResumeAI. All rights reserved.
                  </p>
                </div>
                """.formatted(otp);

            helper.setText(htmlContent, true);
            mailSender.send(message);
            logger.info("OTP email sent successfully to: {}", to);
        } catch (Exception e) {
            logger.error("Failed to send OTP email to {}: {}", to, e.getMessage());
            throw new RuntimeException("Failed to send OTP email. Please try again.");
        }
    }

    @Async
    public void sendPlanChangeEmail(String to, String newPlan) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject("ResumeAI — Subscription Plan Updated");

            String planLabel = "PREMIUM".equals(newPlan) ? "Premium" : "Free";
            String planColor = "PREMIUM".equals(newPlan) ? "#14b8a6" : "#64748b";

            String htmlContent = """
                <div style="font-family:'Segoe UI',Arial,sans-serif;max-width:480px;margin:0 auto;padding:24px;background:#f8fafc;border-radius:12px;">
                  <div style="text-align:center;margin-bottom:20px;">
                    <h2 style="color:#1e293b;margin:0;">Resume<span style="color:#14b8a6;">AI</span></h2>
                  </div>
                  <div style="background:#ffffff;border-radius:8px;padding:24px;box-shadow:0 1px 3px rgba(0,0,0,0.1);">
                    <h3 style="color:#1e293b;margin:0 0 8px;">Subscription Plan Updated</h3>
                    <p style="color:#64748b;font-size:14px;line-height:1.6;margin:0 0 20px;">
                      Your ResumeAI subscription plan has been changed by an administrator.
                    </p>
                    <div style="text-align:center;margin:20px 0;">
                      <span style="display:inline-block;background:%s;color:#ffffff;font-size:18px;padding:10px 24px;border-radius:8px;font-weight:700;">
                        %s Plan
                      </span>
                    </div>
                    <p style="color:#94a3b8;font-size:13px;text-align:center;margin:16px 0 0;">
                      If you have any questions, please contact our support team.
                    </p>
                  </div>
                  <p style="color:#94a3b8;font-size:12px;text-align:center;margin-top:16px;">
                    &copy; 2026 ResumeAI. All rights reserved.
                  </p>
                </div>
                """.formatted(planColor, planLabel);

            helper.setText(htmlContent, true);
            mailSender.send(message);
            logger.info("Plan change email sent successfully to: {}", to);
        } catch (Exception e) {
            logger.error("Failed to send plan change email to {}: {}", to, e.getMessage());
        }
    }

    @Async
    public void sendAccountStatusEmail(String to, boolean suspended) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject("ResumeAI — Account " + (suspended ? "Suspended" : "Reactivated"));

            String statusText = suspended ? "suspended" : "reactivated";
            String statusColor = suspended ? "#ef4444" : "#10b981";
            String statusLabel = suspended ? "SUSPENDED" : "ACTIVE";

            String htmlContent = """
                <div style="font-family:'Segoe UI',Arial,sans-serif;max-width:480px;margin:0 auto;padding:24px;background:#f8fafc;border-radius:12px;">
                  <div style="text-align:center;margin-bottom:20px;">
                    <h2 style="color:#1e293b;margin:0;">Resume<span style="color:#14b8a6;">AI</span></h2>
                  </div>
                  <div style="background:#ffffff;border-radius:8px;padding:24px;box-shadow:0 1px 3px rgba(0,0,0,0.1);">
                    <h3 style="color:#1e293b;margin:0 0 8px;">Account Status Update</h3>
                    <p style="color:#64748b;font-size:14px;line-height:1.6;margin:0 0 20px;">
                      Your ResumeAI account has been <strong>%s</strong> by an administrator.
                    </p>
                    <div style="text-align:center;margin:20px 0;">
                      <span style="display:inline-block;background:%s;color:#ffffff;font-size:16px;padding:10px 24px;border-radius:8px;font-weight:700;">
                        %s
                      </span>
                    </div>
                    <p style="color:#94a3b8;font-size:13px;text-align:center;margin:16px 0 0;">
                      If you believe this is an error, please contact our support team.
                    </p>
                  </div>
                  <p style="color:#94a3b8;font-size:12px;text-align:center;margin-top:16px;">
                    &copy; 2026 ResumeAI. All rights reserved.
                  </p>
                </div>
                """.formatted(statusText, statusColor, statusLabel);

            helper.setText(htmlContent, true);
            mailSender.send(message);
            logger.info("Account status email sent successfully to: {}", to);
        } catch (Exception e) {
            logger.error("Failed to send account status email to {}: {}", to, e.getMessage());
        }
    }

    @Async
    public void sendRoleChangeEmail(String to, String newRole) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject("ResumeAI — Account Role Updated");

            String roleLabel = "ADMIN".equals(newRole) ? "Administrator" : "User";
            String roleColor = "ADMIN".equals(newRole) ? "#7c3aed" : "#0d9488";

            String htmlContent = """
                <div style="font-family:'Segoe UI',Arial,sans-serif;max-width:480px;margin:0 auto;padding:24px;background:#f8fafc;border-radius:12px;">
                  <div style="text-align:center;margin-bottom:20px;">
                    <h2 style="color:#1e293b;margin:0;">Resume<span style="color:#14b8a6;">AI</span></h2>
                  </div>
                  <div style="background:#ffffff;border-radius:8px;padding:24px;box-shadow:0 1px 3px rgba(0,0,0,0.1);">
                    <h3 style="color:#1e293b;margin:0 0 8px;">Account Role Updated</h3>
                    <p style="color:#64748b;font-size:14px;line-height:1.6;margin:0 0 20px;">
                      Your ResumeAI account role has been updated by an administrator.
                    </p>
                    <div style="text-align:center;margin:20px 0;">
                      <span style="display:inline-block;background:%s;color:#ffffff;font-size:18px;padding:10px 24px;border-radius:8px;font-weight:700;">
                        %s
                      </span>
                    </div>
                    <p style="color:#94a3b8;font-size:13px;text-align:center;margin:16px 0 0;">
                      If you have any questions, please contact our support team.
                    </p>
                  </div>
                  <p style="color:#94a3b8;font-size:12px;text-align:center;margin-top:16px;">
                    &copy; 2026 ResumeAI. All rights reserved.
                  </p>
                </div>
                """.formatted(roleColor, roleLabel);

            helper.setText(htmlContent, true);
            mailSender.send(message);
            logger.info("Role change email sent successfully to: {}", to);
        } catch (Exception e) {
            logger.error("Failed to send role change email to {}: {}", to, e.getMessage());
        }
    }

    @Async
    public void sendAccountDeletedEmail(String to) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject("ResumeAI — Account Deleted");

            String htmlContent = """
                <div style="font-family:'Segoe UI',Arial,sans-serif;max-width:480px;margin:0 auto;padding:24px;background:#f8fafc;border-radius:12px;">
                  <div style="text-align:center;margin-bottom:20px;">
                    <h2 style="color:#1e293b;margin:0;">Resume<span style="color:#14b8a6;">AI</span></h2>
                  </div>
                  <div style="background:#ffffff;border-radius:8px;padding:24px;box-shadow:0 1px 3px rgba(0,0,0,0.1);">
                    <h3 style="color:#1e293b;margin:0 0 8px;">Account Deleted</h3>
                    <p style="color:#64748b;font-size:14px;line-height:1.6;margin:0 0 20px;">
                      Your ResumeAI account has been <strong>permanently deleted</strong> by an administrator. All associated data has been removed.
                    </p>
                    <div style="text-align:center;margin:20px 0;">
                      <span style="display:inline-block;background:#ef4444;color:#ffffff;font-size:16px;padding:10px 24px;border-radius:8px;font-weight:700;">
                        ACCOUNT DELETED
                      </span>
                    </div>
                    <p style="color:#94a3b8;font-size:13px;text-align:center;margin:16px 0 0;">
                      If you believe this is an error, please contact our support team.
                    </p>
                  </div>
                  <p style="color:#94a3b8;font-size:12px;text-align:center;margin-top:16px;">
                    &copy; 2026 ResumeAI. All rights reserved.
                  </p>
                </div>
                """;

            helper.setText(htmlContent, true);
            mailSender.send(message);
            logger.info("Account deleted email sent successfully to: {}", to);
        } catch (Exception e) {
            logger.error("Failed to send account deleted email to {}: {}", to, e.getMessage());
        }
    }
}
