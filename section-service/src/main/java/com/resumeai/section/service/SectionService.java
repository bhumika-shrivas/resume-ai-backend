package com.resumeai.section.service;

import com.resumeai.section.entity.Section;
import java.util.List;

public interface SectionService {
    Section addSection(Section section);
    Section getSectionById(Long sectionId);
    List<Section> getSectionsByResume(Long resumeId);
    Section updateSection(Long sectionId, Section updated);
    void deleteSection(Long sectionId);
    void deleteAllSections(Long resumeId);
    List<Section> reorderSections(Long resumeId, List<Section> orderedSections);
    Section toggleVisibility(Long sectionId);
    List<Section> getSectionsByType(Long resumeId, String sectionType);
    List<Section> bulkUpdateSections(Long resumeId, List<Section> sections);
    List<Section> getAiGeneratedSections(Long resumeId);
    Section markAsAiGenerated(Long sectionId, boolean aiGenerated);
    long countSections(Long resumeId);
}