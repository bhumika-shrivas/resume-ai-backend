package com.resumeai.resume.client;

import lombok.Data;

@Data
public class UserDto {
    private Long id;
    private String email;
    private String role;
    private String subscriptionPlan;

    // Explicit getters to ensure they're available
    public String getRole() {
        return this.role;
    }

    public String getSubscriptionPlan() {
        return this.subscriptionPlan;
    }

    public String getEmail() {
        return this.email;
    }

    public Long getId() {
        return this.id;
    }
}
