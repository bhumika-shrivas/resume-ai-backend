package com.resumeai.notification.service;

import com.resumeai.notification.entity.Notification;
import com.resumeai.notification.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.mail.internet.MimeMessage;
import java.util.List;

@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationServiceImpl.class);

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private JavaMailSender emailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Override
    @Transactional
    public void send(Notification notification) {
        notificationRepository.save(notification);

        if ("EMAIL".equals(notification.getChannel()) || "BOTH".equals(notification.getChannel())) {
            sendEmailAsync(notification.getRecipientId(), notification.getTitle(), notification.getMessage());
        }
    }

    @Override
    public void sendBulk(List<String> recipientIds, String title, String message) {
        // Save all notifications to DB first (fast)
        for (String recipientId : recipientIds) {
            Notification n = new Notification();
            n.setRecipientId(recipientId);
            n.setTitle(title);
            n.setMessage(message);
            n.setType("BROADCAST");
            n.setChannel("BOTH");
            notificationRepository.save(n);
        }

        // Send emails asynchronously (non-blocking)
        sendBulkEmailsAsync(recipientIds, title, message);
    }

    @Async
    public void sendBulkEmailsAsync(List<String> recipientIds, String title, String message) {
        logger.info("Starting async email dispatch to {} recipients", recipientIds.size());
        for (String recipientId : recipientIds) {
            sendEmail(recipientId, "[ResumeAI] " + title, message);
        }
        logger.info("Completed async email dispatch to {} recipients", recipientIds.size());
    }

    @Async
    public void sendEmailAsync(String to, String subject, String body) {
        sendEmail(to, subject, body);
    }

    @Override
    @Transactional
    public void markAsRead(Long notificationId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            n.setRead(true);
            notificationRepository.save(n);
        });
    }

    @Override
    @Transactional
    public void markAllRead(String recipientId) {
        List<Notification> unread = notificationRepository.findByRecipientIdAndIsReadOrderBySentAtDesc(recipientId, false);
        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);
    }

    @Override
    public List<Notification> getByRecipient(String recipientId) {
        return notificationRepository.findByRecipientIdOrderBySentAtDesc(recipientId);
    }

    @Override
    public int getUnreadCount(String recipientId) {
        return notificationRepository.countByRecipientIdAndIsRead(recipientId, false);
    }

    @Override
    @Transactional
    public void deleteNotification(Long notificationId) {
        notificationRepository.deleteById(notificationId);
    }

    @Override
    public void sendEmail(String to, String subject, String body) {
        try {
            logger.info("Sending email to: {} | subject: {}", to, subject);
            MimeMessage message = emailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);

            String htmlContent = """
                <div style="font-family:'Segoe UI',Arial,sans-serif;max-width:520px;margin:0 auto;padding:24px;background:#f8fafc;border-radius:12px;">
                  <div style="text-align:center;margin-bottom:20px;">
                    <h2 style="color:#1e293b;margin:0;">Resume<span style="color:#14b8a6;">AI</span></h2>
                  </div>
                  <div style="background:#ffffff;border-radius:8px;padding:24px;box-shadow:0 1px 3px rgba(0,0,0,0.1);">
                    <h3 style="color:#1e293b;margin:0 0 12px;">%s</h3>
                    <p style="color:#475569;font-size:14px;line-height:1.7;margin:0;">%s</p>
                  </div>
                  <p style="color:#94a3b8;font-size:12px;text-align:center;margin-top:16px;">
                    &copy; 2026 ResumeAI. All rights reserved.
                  </p>
                </div>
                """.formatted(subject, body);

            helper.setText(htmlContent, true);
            emailSender.send(message);
            logger.info("Email sent successfully to: {}", to);
        } catch (Exception e) {
            logger.error("Failed to send email to {}: {}", to, e.getMessage(), e);
        }
    }

    @Override
    public List<Notification> getAll() {
        return notificationRepository.findAll();
    }
}
