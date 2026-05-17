package com.resumeai.notification.repository;

import com.resumeai.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientIdOrderBySentAtDesc(String recipientId);

    List<Notification> findByRecipientIdAndIsReadOrderBySentAtDesc(String recipientId, boolean isRead);

    int countByRecipientIdAndIsRead(String recipientId, boolean isRead);

    List<Notification> findByType(String type);

    List<Notification> findByRelatedId(String relatedId);

    void deleteByNotificationId(Long notificationId);
}
