package com.resumeai.resume.service;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.resumeai.resume.entity.Resume;
import com.resumeai.resume.repository.ResumeRepository;
import com.resumeai.resume.client.UserDto;
import com.resumeai.resume.client.TemplateDto;
import com.resumeai.resume.client.NotificationClient;
import com.resumeai.resume.client.AuthClient;
import com.resumeai.resume.client.TemplateClient;

@Service
public class ResumeServiceImpl implements ResumeService {

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private NotificationClient notificationClient;

    @Autowired
    private AuthClient authClient;

    @Autowired
    private TemplateClient templateClient;

    @Override
    public Resume createResume(Resume resume, String userEmail) {
        // Resolve user plan — fall back to FREE if auth-service is unreachable
        String plan = "FREE";
        try {
            UserDto user = authClient.getUserByEmail(userEmail);
            if (user != null && user.getSubscriptionPlan() != null) {
                plan = user.getSubscriptionPlan();
            }
        } catch (Exception e) {
            // Auth-service unavailable — default to FREE
        }
        
        // Enforce Resume Count Limit
        if (!"PREMIUM".equalsIgnoreCase(plan)) {
            long currentCount = resumeRepository.countByUserEmail(userEmail);
            if (currentCount >= 3) {
                throw new RuntimeException("Free tier is limited to 3 resumes. Please upgrade to Premium for unlimited resumes.");
            }
        }

        // Enforce Template Access (skip if template-service is unreachable)
        if (resume.getTemplateId() != null) {
            try {
                TemplateDto template = templateClient.getTemplateById(resume.getTemplateId());
                boolean isPremiumTemplate = template.isPremium();
                if (isPremiumTemplate && !"PREMIUM".equalsIgnoreCase(plan)) {
                    throw new RuntimeException("Selected template is for Premium members only. Please upgrade to use it.");
                }
            } catch (RuntimeException re) {
                if (re.getMessage() != null && re.getMessage().contains("Premium")) {
                    throw re; // Re-throw our own premium check errors
                }
                // Template-service unreachable (FeignException) — allow creation
            } catch (Exception e) {
                // Template-service unreachable — allow creation
            }
        }

        resume.setUserEmail(userEmail);
        if (resume.getStatus() == null || resume.getStatus().isBlank()) {
            resume.setStatus("DRAFT");
        }
        resume.setAtsScore(0);
        resume.setViewCount(0);
        resume.setIsPublic(false);
        return resumeRepository.save(resume);
    }

    @Override
    public Resume getResumeById(Long id, String userEmail) {
        return resumeRepository.findByIdAndUserEmail(id, userEmail)
                .orElseThrow(() -> new RuntimeException("Resume not found or access denied"));
    }

    @Override
    public List<Resume> getResumesByUser(String userEmail) {
        return resumeRepository.findByUserEmail(userEmail);
    }

    @Override
    public Resume updateResume(Long id, Resume updatedResume, String userEmail) {
        Resume existing = getResumeById(id, userEmail);

        if (updatedResume.getTitle() != null) existing.setTitle(updatedResume.getTitle());
        if (updatedResume.getFullName() != null) existing.setFullName(updatedResume.getFullName());
        if (updatedResume.getEmail() != null) existing.setEmail(updatedResume.getEmail());
        if (updatedResume.getPhone() != null) existing.setPhone(updatedResume.getPhone());
        if (updatedResume.getLocation() != null) existing.setLocation(updatedResume.getLocation());
        if (updatedResume.getLinkedin() != null) existing.setLinkedin(updatedResume.getLinkedin());
        if (updatedResume.getWebsite() != null) existing.setWebsite(updatedResume.getWebsite());
        if (updatedResume.getTargetJobTitle() != null) existing.setTargetJobTitle(updatedResume.getTargetJobTitle());
        if (updatedResume.getSummary() != null) existing.setSummary(updatedResume.getSummary());
        if (updatedResume.getTemplateId() != null) {
            try {
                UserDto user = authClient.getUserByEmail(userEmail);
                String plan = user != null && user.getSubscriptionPlan() != null ? user.getSubscriptionPlan() : "FREE";
                
                TemplateDto template = templateClient.getTemplateById(updatedResume.getTemplateId());
                boolean isPremiumTemplate = template.isPremium();
                
                if (isPremiumTemplate && !"PREMIUM".equalsIgnoreCase(plan)) {
                    throw new RuntimeException("Selected template is for Premium members only. Please upgrade to use it.");
                }
            } catch (RuntimeException re) {
                if (re.getMessage() != null && re.getMessage().contains("Premium")) {
                    throw re;
                }
                // Auth/Template service unreachable — allow template change
            } catch (Exception e) {
                // Service unreachable — allow template change
            }
            existing.setTemplateId(updatedResume.getTemplateId());
        }
        if (updatedResume.getStatus() != null) existing.setStatus(updatedResume.getStatus());

        return resumeRepository.save(existing);
    }

    @Override
    @Transactional
    public void deleteResume(Long id, String userEmail) {
        Resume existing = getResumeById(id, userEmail);
        resumeRepository.delete(existing);
    }

    @Override
    public Resume duplicateResume(Long id, String userEmail) {
        Resume original = getResumeById(id, userEmail);

        Resume copy = new Resume();
        copy.setUserEmail(userEmail);
        copy.setTitle("Copy of " + original.getTitle());
        copy.setFullName(original.getFullName());
        copy.setEmail(original.getEmail());
        copy.setPhone(original.getPhone());
        copy.setLocation(original.getLocation());
        copy.setLinkedin(original.getLinkedin());
        copy.setWebsite(original.getWebsite());
        copy.setTargetJobTitle(original.getTargetJobTitle());
        copy.setSummary(original.getSummary());
        copy.setTemplateId(original.getTemplateId());
        copy.setStatus("DRAFT");
        copy.setAtsScore(0);
        copy.setViewCount(0);
        copy.setIsPublic(false);

        return resumeRepository.save(copy);
    }

    @Override
    public Resume updateAtsScore(Long id, Integer score, String userEmail) {
        Resume existing = getResumeById(id, userEmail);
        existing.setAtsScore(score);
        Resume saved = resumeRepository.save(existing);
        
        // Send notification
        java.util.Map<String, Object> notif = new java.util.HashMap<>();
        notif.put("recipientId", userEmail);
        notif.put("title", "ATS Score Computed");
        notif.put("message", "Your resume '" + saved.getTitle() + "' has been analyzed. ATS Score: " + score);
        notif.put("type", "ATS");
        try {
            notificationClient.sendNotification(notif);
        } catch (Exception e) {
            // Ignore notification failures so ATS score still saves
            System.err.println("Failed to send ATS notification: " + e.getMessage());
        }
        
        return saved;
    }

    @Override
    public List<Resume> getPublicResumes() {
        return resumeRepository.findByIsPublicTrue();
    }

    @Override
    @Transactional
    public Resume incrementViewCount(Long id) {
        Resume resume = resumeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Resume not found"));
        resume.setViewCount(resume.getViewCount() + 1);
        return resumeRepository.save(resume);
    }

    @Override
    public List<Resume> getResumesByTemplate(String templateId) {
        return resumeRepository.findByTemplateId(templateId);
    }

    @Override
    public long countUserResumes(String userEmail) {
        return resumeRepository.countByUserEmail(userEmail);
    }

    @Override
    public long countAllResumes() {
        return resumeRepository.count();
    }
}
