package com.resumeai.notification.controller;

import com.resumeai.notification.entity.Notification;
import com.resumeai.notification.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationResource {

    @Autowired
    private NotificationService notificationService;

    @PostMapping("/send")
    public ResponseEntity<Void> send(@RequestBody Notification notification) {
        notificationService.send(notification);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/recipient/{recipientId:.+}")
    public ResponseEntity<List<Notification>> getByRecipient(@PathVariable String recipientId) {
        return ResponseEntity.ok(notificationService.getByRecipient(recipientId));
    }

    @PostMapping("/mark-read/{notificationId}")
    public ResponseEntity<Void> markAsRead(@PathVariable Long notificationId) {
        notificationService.markAsRead(notificationId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/mark-all-read")
    public ResponseEntity<Void> markAllRead(@RequestHeader("X-Auth-User") String recipientId) {
        notificationService.markAllRead(recipientId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Integer> getUnreadCount(@RequestHeader("X-Auth-User") String recipientId) {
        return ResponseEntity.ok(notificationService.getUnreadCount(recipientId));
    }

    @DeleteMapping("/{notificationId}")
    public ResponseEntity<Void> deleteNotification(@PathVariable Long notificationId) {
        notificationService.deleteNotification(notificationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/send-bulk")
    public ResponseEntity<Void> sendBulk(@RequestBody Map<String, Object> payload) {
        @SuppressWarnings("unchecked")
        List<String> recipientIds = (List<String>) payload.get("recipientIds");
        String title = (String) payload.get("title");
        String message = (String) payload.get("message");
        notificationService.sendBulk(recipientIds, title, message);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/all")
    public ResponseEntity<List<Notification>> getAll() {
        return ResponseEntity.ok(notificationService.getAll());
    }
}
