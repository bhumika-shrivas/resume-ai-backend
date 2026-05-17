package com.resumeai.resume.controller;

import com.resumeai.resume.entity.ResumeSection;
import com.resumeai.resume.repository.SectionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/sections")
public class SectionController {

    @Autowired
    private SectionRepository sectionRepository;

    @GetMapping("/resume/{resumeId}")
    public ResponseEntity<List<ResumeSection>> getSectionsByResume(@PathVariable Long resumeId) {
        return ResponseEntity.ok(sectionRepository.findByResumeIdOrderByOrderIndexAsc(resumeId));
    }

    @PostMapping
    public ResponseEntity<ResumeSection> createSection(@RequestBody ResumeSection section) {
        return ResponseEntity.ok(sectionRepository.save(section));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ResumeSection> updateSection(@PathVariable Long id, @RequestBody ResumeSection section) {
        return sectionRepository.findById(id).map(existing -> {
            if (section.getTitle() != null) existing.setTitle(section.getTitle());
            if (section.getContent() != null) existing.setContent(section.getContent());
            if (section.getOrderIndex() != null) existing.setOrderIndex(section.getOrderIndex());
            return ResponseEntity.ok(sectionRepository.save(existing));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSection(@PathVariable Long id) {
        sectionRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/order")
    public ResponseEntity<List<ResumeSection>> updateOrder(@RequestBody List<ResumeSection> sections) {
        sections.forEach(s -> {
            sectionRepository.findById(s.getId()).ifPresent(existing -> {
                existing.setOrderIndex(s.getOrderIndex());
                sectionRepository.save(existing);
            });
        });
        return ResponseEntity.ok(sections);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleError(RuntimeException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }
}
