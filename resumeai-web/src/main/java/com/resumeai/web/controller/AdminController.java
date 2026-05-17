package com.resumeai.web.controller;

import com.resumeai.web.client.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import java.util.Map;

@Controller
@RequestMapping("/web/admin")
public class AdminController {

    @Autowired
    private ResumeClient resumeClient;

    @Autowired
    private TemplateClient templateClient;

    @Autowired
    private NotificationClient notificationClient;

    @GetMapping("/dashboard")
    public ModelAndView adminDashboard() {
        ModelAndView mav = new ModelAndView("admin-dashboard");
        // Add platform-wide stats
        mav.addObject("totalUsers", 1250); // Mock
        mav.addObject("totalResumes", 4500); // Mock
        return mav;
    }

    @GetMapping("/users")
    public ModelAndView manageAllUsers() {
        return new ModelAndView("admin-users");
    }

    @PostMapping("/users/{userId}/suspend")
    public String suspendUser(@PathVariable Long userId) {
        // Logic to call auth-service via Feign
        return "redirect:/web/admin/users";
    }

    @PostMapping("/users/{userId}/subscription")
    public String updateUserSubscription(@PathVariable Long userId, @RequestParam String plan) {
        // Logic to call auth-service
        return "redirect:/web/admin/users";
    }

    @GetMapping("/templates")
    public ModelAndView manageTemplates() {
        ModelAndView mav = new ModelAndView("admin-templates");
        mav.addObject("templates", templateClient.getTemplates());
        return mav;
    }

    @PostMapping("/templates/create")
    public String createTemplate(@RequestBody Map<String, Object> template) {
        // Logic to call template-service
        return "redirect:/web/admin/templates";
    }

    @GetMapping("/analytics")
    public ModelAndView viewPlatformAnalytics() {
        return new ModelAndView("admin-analytics");
    }

    @GetMapping("/ai-usage")
    public ModelAndView viewAiUsageStats() {
        ModelAndView mav = new ModelAndView("admin-ai-usage");
        // Logic to call ai-service for usage logs
        return mav;
    }

    @PostMapping("/notifications/broadcast")
    public String sendPlatformNotification(@RequestParam String message) {
        // Logic to call notification-service bulk send
        return "redirect:/web/admin/dashboard";
    }
}
