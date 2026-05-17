package com.resumeai.payment.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "subscriptions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String userEmail;

    @Column(nullable = false)
    private String planType; // PREMIUM

    private boolean active;
    
    private LocalDateTime startDate;
    private LocalDateTime expiryDate; // null for lifetime

    private String razorpayPaymentId;
    
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }
    public void setPlanType(String planType) { this.planType = planType; }
    public void setActive(boolean active) { this.active = active; }
    public void setStartDate(LocalDateTime startDate) { this.startDate = startDate; }
    public void setExpiryDate(LocalDateTime expiryDate) { this.expiryDate = expiryDate; }
}