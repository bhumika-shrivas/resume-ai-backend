package com.resumeai.ai.client;

import lombok.Data;

@Data
public class UserDto {
    private Long id;
    private String email;
    private String role;
    private String subscriptionPlan;

    // Explicit getters/setters added to avoid relying on Lombok at IDE compile-time
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getSubscriptionPlan() { return subscriptionPlan; }
    public void setSubscriptionPlan(String subscriptionPlan) { this.subscriptionPlan = subscriptionPlan; }
}