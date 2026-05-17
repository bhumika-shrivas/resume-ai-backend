package com.resumeai.jobmatch.service;

import com.resumeai.jobmatch.entity.JobMatch;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface JobMatchService {
    JobMatch analyzeJobFit(Long resumeId, String userId, String jobTitle, String jobDescription, String source);
    JobMatch analyzeJobFitFull(Long resumeId, String userId, String jobTitle, String jobDescription,
                               String source, String companyName, String location, String jobUrl);

    List<JobMatch> getMatchesByResume(Long resumeId);
    List<JobMatch> getMatchesByUser(String userId);
    Optional<JobMatch> getMatchById(Long matchId);

    JobMatch bookmarkMatch(Long matchId, boolean isBookmarked);
    List<JobMatch> getBookmarkedMatches(String userId);

    List<Map<String, Object>> fetchJobsFromLinkedIn(String query, String location);

    JobMatch saveJobDirectly(String userId, String jobTitle, String jobDescription, String source,
                             String companyName, String location, String jobUrl, String salary, String postedAt);

    String getTailoringRecommendations(Long matchId);
    void deleteMatch(Long matchId);
    List<JobMatch> getTopMatches(String userId, int limit);
}
