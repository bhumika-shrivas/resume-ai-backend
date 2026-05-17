package com.resumeai.section.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeai.section.dto.ResumeData;
import com.resumeai.section.entity.Section;
import com.resumeai.section.repository.SectionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Aggregates all sections for a resume into a single ResumeData object.
 * Called by the /aggregate endpoint, which is consumed by template-service and export-service.
 */
@Service
public class SectionAggregatorService {

    @Autowired
    private SectionRepository sectionRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ResumeData aggregateResume(Long resumeId) {
        List<Section> all = sectionRepository.findByResumeIdOrderByDisplayOrderAsc(resumeId);
        ResumeData data = new ResumeData();

        for (Section s : all) {
            if (!Boolean.TRUE.equals(s.getIsVisible())) continue;
            String type = s.getSectionType() != null ? s.getSectionType().toUpperCase() : "";
            String content = s.getContent();
            if (content == null || content.isBlank()) continue;

            try {
                switch (type) {
                    case "PERSONAL_INFO", "PERSONALINFO" -> {
                        Map<String, Object> pi = parseMap(content);
                        data.setPersonalInfo(pi);
                    }
                    case "SUMMARY" -> {
                        // Summary may be stored as plain text or {"summaryText":"..."}
                        if (content.trim().startsWith("{")) {
                            Map<String, Object> m = parseMap(content);
                            Object text = m.get("summaryText");
                            if (text == null) text = m.get("content");
                            data.setSummary(text != null ? text.toString() : content);
                        } else {
                            data.setSummary(content);
                        }
                    }
                    case "EXPERIENCE" -> {
                        List<Map<String, Object>> list = parseList(content);
                        data.setExperience(list);
                    }
                    case "EDUCATION" -> {
                        List<Map<String, Object>> list = parseList(content);
                        data.setEducation(list);
                    }
                    case "SKILLS" -> {
                        // Skills may be stored as ["Java","Python"] or [{"name":"Java",...}]
                        if (content.trim().startsWith("[")) {
                            List<Object> raw = objectMapper.readValue(content, new TypeReference<>() {});
                            List<String> skills = raw.stream().map(item -> {
                                if (item instanceof String s2) return s2;
                                if (item instanceof Map<?,?> m) {
                                    Object name = m.get("name");
                                    return name != null ? name.toString() : item.toString();
                                }
                                return item.toString();
                            }).collect(Collectors.toList());
                            data.setSkills(skills);
                        }
                    }
                    case "CERTIFICATIONS" -> {
                        List<Map<String, Object>> list = parseList(content);
                        data.setCertifications(list);
                    }
                    case "PROJECTS" -> {
                        List<Map<String, Object>> list = parseList(content);
                        data.setProjects(list);
                    }
                    default -> {
                        // Unknown section types are ignored
                    }
                }
            } catch (Exception e) {
                // If parsing fails, skip this section gracefully
            }
        }

        // Ensure lists are never null (return empty list instead)
        if (data.getExperience() == null) data.setExperience(Collections.emptyList());
        if (data.getEducation() == null) data.setEducation(Collections.emptyList());
        if (data.getSkills() == null) data.setSkills(Collections.emptyList());
        if (data.getCertifications() == null) data.setCertifications(Collections.emptyList());
        if (data.getProjects() == null) data.setProjects(Collections.emptyList());
        if (data.getPersonalInfo() == null) data.setPersonalInfo(Collections.emptyMap());

        return data;
    }

    private Map<String, Object> parseMap(String json) throws Exception {
        return objectMapper.readValue(json, new TypeReference<>() {});
    }

    private List<Map<String, Object>> parseList(String json) throws Exception {
        if (json.trim().startsWith("[")) {
            return objectMapper.readValue(json, new TypeReference<>() {});
        }
        // Single object — wrap in list
        Map<String, Object> single = parseMap(json);
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(single);
        return list;
    }
}
