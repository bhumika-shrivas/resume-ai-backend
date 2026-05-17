package com.resumeai.section.controller;

import com.resumeai.section.dto.ResumeData;
import com.resumeai.section.entity.Section;
import com.resumeai.section.service.SectionAggregatorService;
import com.resumeai.section.service.SectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/sections")
public class SectionController {

    @Autowired
    private SectionService sectionService;

    @Autowired
    private SectionAggregatorService sectionAggregatorService;

    /**
     * Aggregate all visible sections for a resume into a single ResumeData DTO.
     * Called internally by template-service (live preview) and export-service (PDF).
     * GET /api/v1/sections/resume/{resumeId}/aggregate
     */
    @GetMapping("/resume/{resumeId}/aggregate")
    public ResponseEntity<ResumeData> aggregate(@PathVariable Long resumeId) {
        return ResponseEntity.ok(sectionAggregatorService.aggregateResume(resumeId));
    }

    @PostMapping
    public ResponseEntity<Section> addSection(@RequestBody Section section) {
        return ResponseEntity.ok(sectionService.addSection(section));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Section> getById(@PathVariable Long id) {
        return ResponseEntity.ok(sectionService.getSectionById(id));
    }

    @GetMapping("/resume/{resumeId}")
    public ResponseEntity<List<Section>> getByResume(@PathVariable Long resumeId) {
        return ResponseEntity.ok(sectionService.getSectionsByResume(resumeId));
    }

    @GetMapping("/resume/{resumeId}/type/{type}")
    public ResponseEntity<List<Section>> getByType(@PathVariable Long resumeId, @PathVariable String type) {
        return ResponseEntity.ok(sectionService.getSectionsByType(resumeId, type));
    }

    @GetMapping("/resume/{resumeId}/ai")
    public ResponseEntity<List<Section>> getAiGenerated(@PathVariable Long resumeId) {
        return ResponseEntity.ok(sectionService.getAiGeneratedSections(resumeId));
    }

    @GetMapping("/resume/{resumeId}/count")
    public ResponseEntity<Long> count(@PathVariable Long resumeId) {
        return ResponseEntity.ok(sectionService.countSections(resumeId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Section> updateSection(@PathVariable Long id, @RequestBody Section section) {
        return ResponseEntity.ok(sectionService.updateSection(id, section));
    }

    @PutMapping("/resume/{resumeId}/reorder")
    public ResponseEntity<List<Section>> reorder(@PathVariable Long resumeId, @RequestBody List<Section> orderedSections) {
        return ResponseEntity.ok(sectionService.reorderSections(resumeId, orderedSections));
    }

    @PutMapping("/{id}/visibility")
    public ResponseEntity<Section> toggleVisibility(@PathVariable Long id) {
        return ResponseEntity.ok(sectionService.toggleVisibility(id));
    }

    @PutMapping("/{id}/ai-flag")
    public ResponseEntity<Section> setAiFlag(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        boolean flag = Boolean.TRUE.equals(body.get("aiGenerated"));
        return ResponseEntity.ok(sectionService.markAsAiGenerated(id, flag));
    }

    @PutMapping("/resume/{resumeId}/bulk")
    public ResponseEntity<List<Section>> bulkUpdate(@PathVariable Long resumeId, @RequestBody List<Section> sections) {
        return ResponseEntity.ok(sectionService.bulkUpdateSections(resumeId, sections));
    }

    /** Legacy PATCH compat for frontend */
    @PatchMapping("/order")
    public ResponseEntity<List<Section>> patchOrder(@RequestBody List<Section> sections) {
        if (sections.isEmpty()) return ResponseEntity.ok(sections);
        Long resumeId = sections.get(0).getResumeId();
        return ResponseEntity.ok(sectionService.reorderSections(resumeId, sections));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSection(@PathVariable Long id) {
        sectionService.deleteSection(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/resume/{resumeId}")
    public ResponseEntity<Void> deleteAll(@PathVariable Long resumeId) {
        sectionService.deleteAllSections(resumeId);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleError(RuntimeException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }
}