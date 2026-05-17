package com.resumeai.template.service;

import com.resumeai.template.dto.TemplateCreateRequest;
import com.resumeai.template.dto.TemplateResponseDTO;
import java.util.List;
import java.util.Optional;

public interface TemplateService {
    TemplateResponseDTO createTemplate(TemplateCreateRequest request);
    Optional<TemplateResponseDTO> getTemplateById(String id);
    List<TemplateResponseDTO> getAllTemplates();
    List<TemplateResponseDTO> getFreeTemplates();
    List<TemplateResponseDTO> getPremiumTemplates();
    List<TemplateResponseDTO> getByCategory(String category);
    List<TemplateResponseDTO> getPopularTemplates();
    TemplateResponseDTO updateTemplate(String id, TemplateCreateRequest request);
    void deactivateTemplate(String id);
    void deleteTemplate(String id);
    void incrementUsage(String id);
}
