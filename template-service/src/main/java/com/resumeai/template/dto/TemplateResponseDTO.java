package com.resumeai.template.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TemplateResponseDTO {
    private String templateId;
    private String name;
    private String description;
    private String templateKey;
    private String category;
    private String thumbnailUrl;
    private String previewImageUrl;
    private String primaryColor;
    private String secondaryColor;
    private String fontFamily;
    private String layoutType;
    private boolean isPremium;
    private boolean isActive;
    private Integer usageCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
