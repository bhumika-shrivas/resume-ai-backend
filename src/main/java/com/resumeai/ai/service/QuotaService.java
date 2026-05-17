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

    private static final int FREE_AI_LIMIT = 5;

    public void checkAiQuota(String email) {
        UserDto user = authClient.getUserByEmail(email);
        if ("PREMIUM".equalsIgnoreCase(user.getSubscriptionPlan()) || "ADMIN".equalsIgnoreCase(user.getRole())) {
            return;
        }

        UserUsageDto usage = authClient.getUsage(email);
        if (usage.getAiCallsThisMonth() + usage.getAtsChecksThisMonth() >= FREE_AI_LIMIT) {
            throw new RuntimeException("AI quota (5) exceeded for this month. Please upgrade to PREMIUM for unlimited access.");
        }
    }

    public void checkAtsQuota(String email) {
        UserDto user = authClient.getUserByEmail(email);
        if ("PREMIUM".equalsIgnoreCase(user.getSubscriptionPlan()) || "ADMIN".equalsIgnoreCase(user.getRole())) {
            return;
        }

        UserUsageDto usage = authClient.getUsage(email);
        if (usage.getAiCallsThisMonth() + usage.getAtsChecksThisMonth() >= FREE_AI_LIMIT) {
            throw new RuntimeException("AI quota (5) exceeded for this month. Please upgrade to PREMIUM for unlimited access.");
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
