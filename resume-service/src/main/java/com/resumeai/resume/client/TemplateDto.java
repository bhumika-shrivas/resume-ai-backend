package com.resumeai.resume.client;

import lombok.Data;

@Data
public class TemplateDto {
    private String templateId;
    private String name;
    private String category;
    private boolean premium;

    // Explicit getters/setters to ensure they're available without Lombok
    public boolean isPremium() {
        return this.premium;
    }

    public void setPremium(boolean premium) {
        this.premium = premium;
    }

    public String getTemplateId() {
        return this.templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public String getName() {
        return this.name;
    }

    public String getCategory() {
        return this.category;
    }
}
