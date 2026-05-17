package com.resumeai.resume.client;

import lombok.Data;

@Data
public class TemplateDto {
    private String templateId;
    private String name;
    private String category;
    private boolean premium;

    // Explicit getter to ensure it's available
    public boolean isPremium() {
        return this.premium;
    }

    public String getTemplateId() {
        return this.templateId;
    }

    public String getName() {
        return this.name;
    }

    public String getCategory() {
        return this.category;
    }
}
