package com.resumeai.resume.service;

import com.resumeai.resume.client.AuthClient;
import com.resumeai.resume.client.UserDto;
import com.resumeai.resume.repository.ResumeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ResumeQuotaService {

    private final AuthClient authClient;
    private final ResumeRepository resumeRepository;
    
    @Autowired
    public ResumeQuotaService(AuthClient authClient, ResumeRepository resumeRepository) {
        this.authClient = authClient;
        this.resumeRepository = resumeRepository;
    }

    private static final int FREE_RESUME_LIMIT = 3;

    public void checkResumeQuota(String email) {
        UserDto user;
        try {
            user = authClient.getUserByEmail(email);
        } catch (Exception e) {
            // If auth-service is unreachable, allow creation gracefully
            return;
        }
        if ("PREMIUM".equalsIgnoreCase(user.getSubscriptionPlan()) || "ADMIN".equalsIgnoreCase(user.getRole())) {
            return;
        }

        long count = resumeRepository.countByUserEmail(email);
        if (count >= FREE_RESUME_LIMIT) {
            throw new RuntimeException("Resume limit reached (3). Please upgrade to PREMIUM to create unlimited resumes.");
        }
    }
}
