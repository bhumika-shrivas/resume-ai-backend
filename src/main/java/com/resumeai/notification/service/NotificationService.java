package com.resumeai.notification.service;

import com.resumeai.notification.entity.Notification;
import java.util.List;

public interface NotificationService {
    void send(Notification notification);
    void sendBulk(List<String> recipientIds, String title, String message);
    
    void markAsRead(Long notificationId);
    void markAllRead(String recipientId);
    
    List<Notification> getByRecipient(String recipientId);
    int getUnreadCount(String recipientId);
    
    void deleteNotification(Long notificationId);
    void sendEmail(String to, String subject, String body);
    
    List<Notification> getAll();
}
