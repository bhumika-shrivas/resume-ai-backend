package com.resumeai.auth.dto;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
public class UserDTO {
    private Long id;
    private String fullName;
    private String email;
    private String role;
    private String subscriptionPlan;
    private String headline;
    private String about;
    private String provider;
    private boolean active;

    public UserDTO(Long id,
                   String fullName,
                   String email,
                   String role,
                   String subscriptionPlan,
                   String headline,
                   String about,
                   String provider,
                   boolean active) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.role = role;
        this.subscriptionPlan = subscriptionPlan;
        this.headline = headline;
        this.about = about;
        this.provider = provider;
        this.active = active;
    }
}