package com.resumeai.section.service;

import com.resumeai.section.entity.Section;
import com.resumeai.section.repository.SectionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SectionServiceImpl implements SectionService {

    @Autowired
    private SectionRepository sectionRepository;

    @Override
    public Section addSection(Section section) {
        if (section.getDisplayOrder() == null || section.getDisplayOrder() < 0) {
            Integer maxOrder = sectionRepository.findMaxDisplayOrder(section.getResumeId());
            section.setDisplayOrder(maxOrder == null ? 0 : maxOrder + 1);
        }
        if (section.getIsVisible() == null)   section.setIsVisible(true);
        if (section.getAiGenerated() == null) section.setAiGenerated(false);
        return sectionRepository.save(section);
    }

    @Override
    public Section getSectionById(Long sectionId) {
        return sectionRepository.findById(sectionId)
                .orElseThrow(() -> new RuntimeException("Section not found: " + sectionId));
    }

    @Override
    public List<Section> getSectionsByResume(Long resumeId) {
        return sectionRepository.findByResumeIdOrderByDisplayOrderAsc(resumeId);
    }

    @Override
    public Section updateSection(Long sectionId, Section updated) {
        Section existing = getSectionById(sectionId);
        if (updated.getTitle() != null)        existing.setTitle(updated.getTitle());
        if (updated.getContent() != null)      existing.setContent(updated.getContent());
        if (updated.getSectionType() != null)  existing.setSectionType(updated.getSectionType());
        if (updated.getDisplayOrder() != null) existing.setDisplayOrder(updated.getDisplayOrder());
        if (updated.getIsVisible() != null)    existing.setIsVisible(updated.getIsVisible());
        if (updated.getAiGenerated() != null)  existing.setAiGenerated(updated.getAiGenerated());
        return sectionRepository.save(existing);
    }

    @Override
    @Transactional
    public void deleteSection(Long sectionId) {
        if (!sectionRepository.existsById(sectionId))
            throw new RuntimeException("Section not found: " + sectionId);
        sectionRepository.deleteById(sectionId);
    }

    @Override
    @Transactional
    public void deleteAllSections(Long resumeId) {
        sectionRepository.deleteByResumeId(resumeId);
    }

    @Override
    @Transactional
    public List<Section> reorderSections(Long resumeId, List<Section> orderedSections) {
        for (Section item : orderedSections) {
            if (item.getSectionId() != null && item.getDisplayOrder() != null) {
                sectionRepository.updateDisplayOrder(item.getSectionId(), item.getDisplayOrder());
            }
        }
        return sectionRepository.findByResumeIdOrderByDisplayOrderAsc(resumeId);
    }

    @Override
    public Section toggleVisibility(Long sectionId) {
        Section existing = getSectionById(sectionId);
        existing.setIsVisible(!Boolean.TRUE.equals(existing.getIsVisible()));
        return sectionRepository.save(existing);
    }

    @Override
    public List<Section> getSectionsByType(Long resumeId, String sectionType) {
        return sectionRepository.findByResumeIdAndSectionType(resumeId, sectionType.toUpperCase());
    }

    @Override
    @Transactional
    public List<Section> bulkUpdateSections(Long resumeId, List<Section> sections) {
        for (Section s : sections) {
            if (s.getSectionId() != null) {
                sectionRepository.findById(s.getSectionId()).ifPresent(existing -> {
                    if (s.getTitle() != null)        existing.setTitle(s.getTitle());
                    if (s.getContent() != null)      existing.setContent(s.getContent());
                    if (s.getDisplayOrder() != null) existing.setDisplayOrder(s.getDisplayOrder());
                    if (s.getIsVisible() != null)    existing.setIsVisible(s.getIsVisible());
                    if (s.getAiGenerated() != null)  existing.setAiGenerated(s.getAiGenerated());
                    sectionRepository.save(existing);
                });
            }
        }
        return sectionRepository.findByResumeIdOrderByDisplayOrderAsc(resumeId);
    }

    @Override
    public List<Section> getAiGeneratedSections(Long resumeId) {
        return sectionRepository.findByResumeId(resumeId).stream()
                .filter(s -> Boolean.TRUE.equals(s.getAiGenerated()))
                .toList();
    }

    @Override
    public Section markAsAiGenerated(Long sectionId, boolean aiGenerated) {
        Section existing = getSectionById(sectionId);
        existing.setAiGenerated(aiGenerated);
        return sectionRepository.save(existing);
    }

    @Override
    public long countSections(Long resumeId) {
        return sectionRepository.countByResumeId(resumeId);
    }
}
