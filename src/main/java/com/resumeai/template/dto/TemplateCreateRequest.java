package com.resumeai.template.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TemplateCreateRequest {
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
}
