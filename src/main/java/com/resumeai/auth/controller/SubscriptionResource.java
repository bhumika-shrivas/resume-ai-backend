package com.resumeai.auth.controller;

import com.resumeai.auth.entity.User;
import com.resumeai.auth.entity.UserUsage;
import com.resumeai.auth.service.SubscriptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/subscription")
public class SubscriptionResource {

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private com.resumeai.auth.service.AuthService authService;

    @PostMapping("/upgrade/{email:.+}")
    public ResponseEntity<User> upgrade(@PathVariable String email) {
        User user = authService.getUserByEmail(email);
        return ResponseEntity.ok(subscriptionService.upgradeToPremium(user.getId()));
    }

    @GetMapping("/usage/{email:.+}")
    public ResponseEntity<UserUsage> getUsage(@PathVariable String email) {
        User user = authService.getUserByEmail(email);
        return ResponseEntity.ok(subscriptionService.getUsage(user.getId()));
    }

    @PostMapping("/usage/{email:.+}/increment-ai")
    public ResponseEntity<Void> incrementAi(@PathVariable String email) {
        User user = authService.getUserByEmail(email);
        subscriptionService.incrementAiCall(user.getId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/usage/{email:.+}/increment-ats")
    public ResponseEntity<Void> incrementAts(@PathVariable String email) {
        User user = authService.getUserByEmail(email);
        subscriptionService.incrementAtsCheck(user.getId());
        return ResponseEntity.ok().build();
    }
}
