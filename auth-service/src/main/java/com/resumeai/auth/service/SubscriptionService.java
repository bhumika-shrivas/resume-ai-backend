package com.resumeai.auth.service;

import com.resumeai.auth.entity.User;
import com.resumeai.auth.entity.UserUsage;
import com.resumeai.auth.repository.UserRepository;
import com.resumeai.auth.repository.UserUsageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class SubscriptionService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserUsageRepository usageRepository;

    @Transactional
    public User upgradeToPremium(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        user.setSubscriptionPlan("PREMIUM");
        return userRepository.save(user);
    }

    @Transactional
    public User downgradeToFree(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        user.setSubscriptionPlan("FREE");
        return userRepository.save(user);
    }

    public UserUsage getUsage(Long userId) {
        return usageRepository.findById(userId)
                .orElseGet(() -> {
                    UserUsage newUsage = new UserUsage(userId);
                    return usageRepository.save(newUsage);
                });
    }

    @Transactional
    public void incrementAiCall(Long userId) {
        UserUsage usage = getUsage(userId);
        usage.setAiCallsThisMonth(usage.getAiCallsThisMonth() + 1);
        usageRepository.save(usage);
    }

    @Transactional
    public void incrementAtsCheck(Long userId) {
        UserUsage usage = getUsage(userId);
        usage.setAtsChecksThisMonth(usage.getAtsChecksThisMonth() + 1);
        usageRepository.save(usage);
    }
}
