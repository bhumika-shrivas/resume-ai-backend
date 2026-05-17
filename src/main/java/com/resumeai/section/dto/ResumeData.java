package com.resumeai.section.dto;

import java.util.List;
import java.util.Map;

/**
 * Fully assembled resume data DTO.
 * Built by SectionAggregatorService from all section rows for a given resumeId.
 * Consumed by template-service (preview) and export-service (PDF generation).
 */
public class ResumeData {

    private Map<String, Object> personalInfo;
    private String summary;
    private List<Map<String, Object>> experience;
    private List<Map<String, Object>> education;
    private List<String> skills;
    private List<Map<String, Object>> certifications;
    private List<Map<String, Object>> projects;

    public ResumeData() {}

    public Map<String, Object> getPersonalInfo() { return personalInfo; }
    public void setPersonalInfo(Map<String, Object> personalInfo) { this.personalInfo = personalInfo; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public List<Map<String, Object>> getExperience() { return experience; }
    public void setExperience(List<Map<String, Object>> experience) { this.experience = experience; }

    public List<Map<String, Object>> getEducation() { return education; }
    public void setEducation(List<Map<String, Object>> education) { this.education = education; }

    public List<String> getSkills() { return skills; }
    public void setSkills(List<String> skills) { this.skills = skills; }

    public List<Map<String, Object>> getCertifications() { return certifications; }
    public void setCertifications(List<Map<String, Object>> certifications) { this.certifications = certifications; }

    public List<Map<String, Object>> getProjects() { return projects; }
    public void setProjects(List<Map<String, Object>> projects) { this.projects = projects; }
}
