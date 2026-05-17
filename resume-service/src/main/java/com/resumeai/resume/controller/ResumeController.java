package com.resumeai.resume.controller;

import com.resumeai.resume.entity.Resume;
import com.resumeai.resume.service.ResumeService;
import com.resumeai.resume.service.ResumeQuotaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/resumes")
public class ResumeController {

    private final ResumeService resumeService;
    private final ResumeQuotaService quotaService;

    @Autowired
    public ResumeController(ResumeService resumeService, ResumeQuotaService quotaService) {
        this.resumeService = resumeService;
        this.quotaService = quotaService;
    }

    // ── CREATE ──────────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<Resume> createResume(
            @RequestBody Resume resume,
            @RequestHeader("X-Auth-User") String userEmail) {
        quotaService.checkResumeQuota(userEmail);
        return ResponseEntity.ok(resumeService.createResume(resume, userEmail));
    }

    @PostMapping("/{id}/duplicate")
    public ResponseEntity<Resume> duplicateResume(
            @PathVariable Long id,
            @RequestHeader("X-Auth-User") String userEmail) {
        quotaService.checkResumeQuota(userEmail);
        return ResponseEntity.ok(resumeService.duplicateResume(id, userEmail));
    }

    // ── READ ─────────────────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<List<Resume>> getMyResumes(
            @RequestHeader("X-Auth-User") String userEmail) {
        return ResponseEntity.ok(resumeService.getResumesByUser(userEmail));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Resume> getResume(
            @PathVariable Long id,
            @RequestHeader("X-Auth-User") String userEmail) {
        return ResponseEntity.ok(resumeService.getResumeById(id, userEmail));
    }

    @GetMapping("/public")
    public ResponseEntity<List<Resume>> getPublicResumes() {
        return ResponseEntity.ok(resumeService.getPublicResumes());
    }

    @GetMapping("/template/{templateId}")
    public ResponseEntity<List<Resume>> getByTemplate(@PathVariable String templateId) {
        return ResponseEntity.ok(resumeService.getResumesByTemplate(templateId));
    }

    @GetMapping("/count")
    public ResponseEntity<Long> countResumes(
            @RequestHeader("X-Auth-User") String userEmail) {
        return ResponseEntity.ok(resumeService.countUserResumes(userEmail));
    }

    @GetMapping("/admin/stats")
    public ResponseEntity<Map<String, Object>> getAdminStats() {
        return ResponseEntity.ok(Map.of("totalResumes", resumeService.countAllResumes()));
    }

    // ── UPDATE ───────────────────────────────────────────────────────────────

    @PutMapping("/{id}")
    public ResponseEntity<Resume> updateResume(
            @PathVariable Long id,
            @RequestBody Resume resume,
            @RequestHeader("X-Auth-User") String userEmail) {
        return ResponseEntity.ok(resumeService.updateResume(id, resume, userEmail));
    }

    @PutMapping("/{id}/ats-score")
    public ResponseEntity<Resume> updateAtsScore(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            @RequestHeader("X-Auth-User") String userEmail) {
        
        Integer score = 0;
        if (body.containsKey("score") && body.get("score") != null) {
            Object s = body.get("score");
            if (s instanceof Number) {
                score = ((Number) s).intValue();
            } else {
                try { score = Integer.parseInt(s.toString()); } catch(Exception e) {}
            }
        }
        
        return ResponseEntity.ok(resumeService.updateAtsScore(id, score, userEmail));
    }

    @PutMapping("/{id}/view")
    public ResponseEntity<Resume> incrementView(@PathVariable Long id) {
        return ResponseEntity.ok(resumeService.incrementViewCount(id));
    }

    // ── DELETE ───────────────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteResume(
            @PathVariable Long id,
            @RequestHeader("X-Auth-User") String userEmail) {
        resumeService.deleteResume(id, userEmail);
        return ResponseEntity.noContent().build();
    }

    // ── ERROR HANDLING ───────────────────────────────────────────────────────

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleError(RuntimeException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }
}