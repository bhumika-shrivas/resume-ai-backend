package com.resumeai.ai.service;

import com.resumeai.ai.client.AuthClient;
import com.resumeai.ai.client.UserDto;
import com.resumeai.ai.client.UserUsageDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class QuotaService {

    @Autowired
    private AuthClient authClient;

    public void validatePremiumAccess(String plan, String role) {
        if (!"PREMIUM".equalsIgnoreCase(plan) && !"ADMIN".equalsIgnoreCase(role)) {
            throw new com.resumeai.ai.exception.PremiumFeatureException("This is a Premium feature. Please upgrade your plan to access this service.");
        }
    }

    public void incrementAiUsage(String email) {
        UserDto user = authClient.getUserByEmail(email);
        authClient.incrementAi(email);
    }

    public void incrementAtsUsage(String email) {
        UserDto user = authClient.getUserByEmail(email);
        authClient.incrementAts(email);
    }
}
