package com.resumeai.section.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "resume_sections", indexes = {
    @Index(name = "idx_resume_id", columnList = "resumeId"),
    @Index(name = "idx_section_type", columnList = "sectionType"),
    @Index(name = "idx_resume_display", columnList = "resumeId, displayOrder")
})
public class Section {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long sectionId;

    @Column(nullable = false)
    private Long resumeId;

    /**
     * Section type: SUMMARY, EXPERIENCE, EDUCATION, SKILLS,
     * CERTIFICATIONS, PROJECTS, LANGUAGES, VOLUNTEER, CUSTOM
     */
    @Column(nullable = false, length = 50)
    private String sectionType;

    @Column(nullable = false)
    private String title;

    /**
     * Rich text content stored as JSON string.
     * Supports both plain text and structured JSON for complex layouts.
     */
    @Column(columnDefinition = "TEXT")
    private String content;

    /** Drag-and-drop order within the resume */
    @Column(nullable = false)
    private Integer displayOrder = 0;

    /** Whether section is shown in the public/export view */
    @Column(nullable = false)
    private Boolean isVisible = true;

    /** Marks content that was generated/suggested by the AI service */
    @Column(nullable = false)
    private Boolean aiGenerated = false;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Section() {}

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (isVisible == null)    isVisible   = true;
        if (aiGenerated == null)  aiGenerated = false;
        if (displayOrder == null) displayOrder = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ── Getters & Setters ──────────────────────────────────────────────────

    public Long getSectionId()                  { return sectionId; }
    public void setSectionId(Long sectionId)    { this.sectionId = sectionId; }

    public Long getResumeId()                   { return resumeId; }
    public void setResumeId(Long resumeId)      { this.resumeId = resumeId; }

    public String getSectionType()              { return sectionType; }
    public void setSectionType(String t)        { this.sectionType = t; }

    public String getTitle()                    { return title; }
    public void setTitle(String title)          { this.title = title; }

    public String getContent()                  { return content; }
    public void setContent(String content)      { this.content = content; }

    public Integer getDisplayOrder()            { return displayOrder; }
    public void setDisplayOrder(Integer order)  { this.displayOrder = order; }

    public Boolean getIsVisible()               { return isVisible; }
    public void setIsVisible(Boolean visible)   { this.isVisible = visible; }

    public Boolean getAiGenerated()             { return aiGenerated; }
    public void setAiGenerated(Boolean ai)      { this.aiGenerated = ai; }

    public LocalDateTime getCreatedAt()         { return createdAt; }
    public void setCreatedAt(LocalDateTime t)   { this.createdAt = t; }

    public LocalDateTime getUpdatedAt()         { return updatedAt; }
    public void setUpdatedAt(LocalDateTime t)   { this.updatedAt = t; }
}