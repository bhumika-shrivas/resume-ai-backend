package com.resumeai.resume.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "resumes")
public class Resume {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String userEmail;

    /** Resume document name — e.g. "My Software Engineer Resume" */
    @Column(nullable = false)
    private String title;

    // ── Personal Info fields ──────────────────────────────────────────────
    /** Person's real full name — shown on the resume header */
    private String fullName;
    private String email;
    private String phone;
    private String location;
    private String linkedin;
    private String website;
    // ─────────────────────────────────────────────────────────────────────

    private String targetJobTitle;

    @Column(columnDefinition = "TEXT")
    private String summary;

    private String templateId;

    private Integer atsScore = 0;

    // DRAFT or COMPLETE
    private String status = "DRAFT";

    @JsonProperty("isPublic")
    private Boolean isPublic = false;

    private Integer viewCount = 0;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Resume() {}

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (atsScore == null)  atsScore  = 0;
        if (viewCount == null) viewCount = 0;
        if (isPublic == null)  isPublic  = false;
        if (status == null || status.isBlank()) status = "DRAFT";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters & Setters
    public Long getId()                         { return id; }
    public void setId(Long id)                  { this.id = id; }

    public String getUserEmail()                { return userEmail; }
    public void setUserEmail(String v)          { this.userEmail = v; }

    public String getTitle()                    { return title; }
    public void setTitle(String v)              { this.title = v; }

    public String getFullName()                 { return fullName; }
    public void setFullName(String v)           { this.fullName = v; }

    public String getEmail()                    { return email; }
    public void setEmail(String v)              { this.email = v; }

    public String getPhone()                    { return phone; }
    public void setPhone(String v)              { this.phone = v; }

    public String getLocation()                 { return location; }
    public void setLocation(String v)           { this.location = v; }

    public String getLinkedin()                 { return linkedin; }
    public void setLinkedin(String v)           { this.linkedin = v; }

    public String getWebsite()                  { return website; }
    public void setWebsite(String v)            { this.website = v; }

    public String getTargetJobTitle()           { return targetJobTitle; }
    public void setTargetJobTitle(String v)     { this.targetJobTitle = v; }

    public String getSummary()                  { return summary; }
    public void setSummary(String v)            { this.summary = v; }

    public String getTemplateId()               { return templateId; }
    public void setTemplateId(String v)         { this.templateId = v; }

    public Integer getAtsScore()                { return atsScore; }
    public void setAtsScore(Integer v)          { this.atsScore = v; }

    public String getStatus()                   { return status; }
    public void setStatus(String v)             { this.status = v; }

    @JsonProperty("isPublic")
    public Boolean getIsPublic()                { return isPublic; }
    public void setIsPublic(Boolean v)          { this.isPublic = v; }

    public Integer getViewCount()               { return viewCount; }
    public void setViewCount(Integer v)         { this.viewCount = v; }

    public LocalDateTime getCreatedAt()         { return createdAt; }
    public void setCreatedAt(LocalDateTime v)   { this.createdAt = v; }

    public LocalDateTime getUpdatedAt()         { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v)   { this.updatedAt = v; }
}