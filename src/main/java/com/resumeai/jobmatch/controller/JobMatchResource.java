package com.resumeai.jobmatch.controller;

import com.resumeai.jobmatch.entity.JobMatch;
import com.resumeai.jobmatch.service.JobMatchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/job-matches")
public class JobMatchResource {

    @Autowired
    private JobMatchService jobMatchService;

    @PostMapping("/analyze")
    public ResponseEntity<JobMatch> analyzeJobFit(
            @RequestHeader("X-Auth-User") String userId,
            @RequestBody Map<String, Object> payload) {
        Long resumeId = Long.parseLong(payload.get("resumeId").toString());
        String jobTitle = (String) payload.get("jobTitle");
        String jobDescription = (String) payload.get("jobDescription");
        String source = (String) payload.get("source");
        String companyName = (String) payload.get("companyName");
        String location = (String) payload.get("location");
        String jobUrl = (String) payload.get("jobUrl");

        return ResponseEntity.ok(jobMatchService.analyzeJobFitFull(
                resumeId, userId, jobTitle, jobDescription, source, companyName, location, jobUrl));
    }

    @PostMapping("/save-job")
    public ResponseEntity<JobMatch> saveJobDirectly(
            @RequestHeader("X-Auth-User") String userId,
            @RequestBody Map<String, Object> payload) {
        String jobTitle = (String) payload.get("jobTitle");
        String jobDescription = (String) payload.getOrDefault("jobDescription", "");
        String source = (String) payload.getOrDefault("source", "LINKEDIN");
        String companyName = (String) payload.get("companyName");
        String location = (String) payload.get("location");
        String jobUrl = (String) payload.get("jobUrl");
        String salary = (String) payload.getOrDefault("salary", "");
        String postedAt = (String) payload.getOrDefault("postedAt", "");
        return ResponseEntity.ok(jobMatchService.saveJobDirectly(
                userId, jobTitle, jobDescription, source, companyName, location, jobUrl, salary, postedAt));
    }

    @GetMapping("/resume/{resumeId}")
    public ResponseEntity<List<JobMatch>> getByResume(@PathVariable Long resumeId) {
        return ResponseEntity.ok(jobMatchService.getMatchesByResume(resumeId));
    }

    @GetMapping("/user/{userId:.+}")
    public ResponseEntity<List<JobMatch>> getByUser(@PathVariable String userId) {
        return ResponseEntity.ok(jobMatchService.getMatchesByUser(userId));
    }

    @GetMapping("/{matchId}")
    public ResponseEntity<JobMatch> getById(@PathVariable Long matchId) {
        return jobMatchService.getMatchById(matchId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/bookmark/{matchId}")
    public ResponseEntity<JobMatch> bookmarkMatch(
            @PathVariable Long matchId,
            @RequestParam boolean isBookmarked) {
        return ResponseEntity.ok(jobMatchService.bookmarkMatch(matchId, isBookmarked));
    }

    @GetMapping("/bookmarks")
    public ResponseEntity<List<JobMatch>> getBookmarks(
            @RequestHeader("X-Auth-User") String userId) {
        return ResponseEntity.ok(jobMatchService.getBookmarkedMatches(userId));
    }

    @GetMapping("/fetchLinkedIn")
    public ResponseEntity<?> fetchLinkedIn(
            @RequestHeader(value = "X-Auth-Plan", defaultValue = "FREE") String plan,
            @RequestHeader(value = "X-Auth-Role", defaultValue = "USER") String role,
            @RequestParam String query,
            @RequestParam(defaultValue = "") String location) {
        
        if (!"PREMIUM".equalsIgnoreCase(plan) && !"ADMIN".equalsIgnoreCase(role)) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Job matching is a Premium feature. Please upgrade to use this feature."));
        }
        
        return ResponseEntity.ok(jobMatchService.fetchJobsFromLinkedIn(query, location));
    }

    @GetMapping("/recommendations/{matchId}")
    public ResponseEntity<String> getRecommendations(@PathVariable Long matchId) {
        return ResponseEntity.ok(jobMatchService.getTailoringRecommendations(matchId));
    }

    @GetMapping("/top")
    public ResponseEntity<List<JobMatch>> getTopMatches(
            @RequestHeader("X-Auth-User") String userId,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(jobMatchService.getTopMatches(userId, limit));
    }

    @DeleteMapping("/{matchId}")
    public ResponseEntity<Void> deleteMatch(@PathVariable Long matchId) {
        jobMatchService.deleteMatch(matchId);
        return ResponseEntity.noContent().build();
    }
}
