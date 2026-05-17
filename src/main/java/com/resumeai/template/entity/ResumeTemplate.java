package com.resumeai.template.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.*;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "resume_templates")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResumeTemplate {

    @Id
    @Column(name = "template_id", updatable = false, nullable = false)
    private String templateId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "template_key", unique = true, nullable = false)
    private String templateKey;

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    @Column(name = "preview_image_url")
    private String previewImageUrl;

    @Column(name = "primary_color")
    private String primaryColor;

    @Column(name = "secondary_color")
    private String secondaryColor;

    @Column(name = "font_family")
    private String fontFamily;

    @Column(name = "layout_type")
    private String layoutType;

    @Column(name = "is_premium", nullable = false)
    private boolean isPremium;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column(name = "usage_count")
    private Integer usageCount;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();

        if (this.templateId == null) {
            this.templateId = UUID.randomUUID().toString();
        }

        if (this.usageCount == null) {
            this.usageCount = 0;
        }

        if (this.templateKey == null && this.name != null) {
            this.templateKey = this.name.toLowerCase().replace(" ", "-");
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}