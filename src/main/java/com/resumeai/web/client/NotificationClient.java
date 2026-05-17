package com.resumeai.web.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@FeignClient(name = "notification-service")
public interface NotificationClient {
    @GetMapping("/api/v1/notifications/recipient/{userId}")
    List<Map<String, Object>> getNotifications(@PathVariable String userId);
    
    @GetMapping("/api/v1/notifications/unread-count")
    Integer getUnreadCount(@RequestHeader("X-Auth-User") String userId);
}
