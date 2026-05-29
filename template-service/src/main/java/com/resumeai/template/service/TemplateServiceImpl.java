package com.resumeai.template.service;

import com.resumeai.template.dto.TemplateCreateRequest;
import com.resumeai.template.dto.TemplateResponseDTO;
import com.resumeai.template.entity.ResumeTemplate;
import com.resumeai.template.repository.TemplateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class TemplateServiceImpl implements TemplateService {

    @Autowired
    private TemplateRepository templateRepository;

    @Autowired
    private com.resumeai.template.repository.TemplateUsageLogRepository usageLogRepository;

    private TemplateResponseDTO mapToDTO(ResumeTemplate entity) {
        long thisMonth = 0;
        long lastMonth = 0;
        Double trend = 0.0;
        try {
            thisMonth = usageLogRepository.countUsageBetween(entity.getTemplateId(), LocalDateTime.now().minusDays(30), LocalDateTime.now());
            lastMonth = usageLogRepository.countUsageBetween(entity.getTemplateId(), LocalDateTime.now().minusDays(60), LocalDateTime.now().minusDays(30));
            if (lastMonth > 0) {
                trend = ((double) (thisMonth - lastMonth) / lastMonth) * 100;
            } else if (thisMonth > 0) {
                trend = 100.0;
            }
        } catch (Exception e) {
            // Ignore if table not yet created
        }

        return TemplateResponseDTO.builder()
                .templateId(entity.getTemplateId())
                .name(entity.getName())
                .description(entity.getDescription())
                .templateKey(entity.getTemplateKey())
                .category(entity.getCategory())
                .thumbnailUrl(entity.getThumbnailUrl())
                .previewImageUrl(entity.getPreviewImageUrl())
                .primaryColor(entity.getPrimaryColor())
                .secondaryColor(entity.getSecondaryColor())
                .fontFamily(entity.getFontFamily())
                .layoutType(entity.getLayoutType())
                .isPremium(entity.isPremium())
                .isActive(entity.isActive())
                .usageCount(entity.getUsageCount())
                .trend(Math.round(trend * 10.0) / 10.0)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private ResumeTemplate mapToEntity(TemplateCreateRequest dto) {
        return ResumeTemplate.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .templateKey(dto.getTemplateKey())
                .category(dto.getCategory())
                .thumbnailUrl(dto.getThumbnailUrl())
                .previewImageUrl(dto.getPreviewImageUrl())
                .primaryColor(dto.getPrimaryColor())
                .secondaryColor(dto.getSecondaryColor())
                .fontFamily(dto.getFontFamily())
                .layoutType(dto.getLayoutType())
                .isPremium(dto.isPremium())
                .isActive(dto.isActive())
                .build();
    }

    @Override
    public TemplateResponseDTO createTemplate(TemplateCreateRequest request) {
        ResumeTemplate template = mapToEntity(request);
        ResumeTemplate saved = templateRepository.save(template);
        return mapToDTO(saved);
    }

    @Override
    public Optional<TemplateResponseDTO> getTemplateById(String idOrKey) {
        Optional<ResumeTemplate> template = templateRepository.findById(idOrKey);
        if (template.isEmpty()) {
            template = templateRepository.findByTemplateKey(idOrKey);
        }
        return template.map(this::mapToDTO);
    }

    @Override
    public List<TemplateResponseDTO> getAllTemplates() {
        return templateRepository.findAll().stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Override
    public List<TemplateResponseDTO> getFreeTemplates() {
        return templateRepository.findByIsPremiumAndIsActiveTrue(false).stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Override
    public List<TemplateResponseDTO> getPremiumTemplates() {
        return templateRepository.findByIsPremiumAndIsActiveTrue(true).stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Override
    public List<TemplateResponseDTO> getByCategory(String category) {
        return templateRepository.findByCategoryAndIsActiveTrue(category).stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Override
    public List<TemplateResponseDTO> getPopularTemplates() {
        return templateRepository.findByIsActiveTrueOrderByUsageCountDesc().stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Override
    public TemplateResponseDTO updateTemplate(String id, TemplateCreateRequest request) {
        ResumeTemplate existing = templateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Template not found: " + id));

        if (request.getName() != null) existing.setName(request.getName());
        if (request.getDescription() != null) existing.setDescription(request.getDescription());
        if (request.getTemplateKey() != null) existing.setTemplateKey(request.getTemplateKey());
        if (request.getCategory() != null) existing.setCategory(request.getCategory());
        if (request.getThumbnailUrl() != null) existing.setThumbnailUrl(request.getThumbnailUrl());
        if (request.getPreviewImageUrl() != null) existing.setPreviewImageUrl(request.getPreviewImageUrl());
        if (request.getPrimaryColor() != null) existing.setPrimaryColor(request.getPrimaryColor());
        if (request.getSecondaryColor() != null) existing.setSecondaryColor(request.getSecondaryColor());
        if (request.getFontFamily() != null) existing.setFontFamily(request.getFontFamily());
        if (request.getLayoutType() != null) existing.setLayoutType(request.getLayoutType());
        
        existing.setPremium(request.isPremium());
        existing.setActive(request.isActive());
        
        existing.setUpdatedAt(LocalDateTime.now());

        ResumeTemplate updated = templateRepository.save(existing);
        return mapToDTO(updated);
    }

    @Override
    public void deactivateTemplate(String id) {
        templateRepository.findById(id).ifPresent(t -> {
            t.setActive(false);
            templateRepository.save(t);
        });
    }

    @Override
    public void deleteTemplate(String id) {
        templateRepository.deleteById(id);
    }

    @Override
    public void incrementUsage(String id) {
        templateRepository.incrementUsageCount(id);
        try {
            usageLogRepository.save(com.resumeai.template.entity.TemplateUsageLog.builder()
                    .templateId(id)
                    .usedAt(LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            // Log table might not exist yet
        }
    }
}