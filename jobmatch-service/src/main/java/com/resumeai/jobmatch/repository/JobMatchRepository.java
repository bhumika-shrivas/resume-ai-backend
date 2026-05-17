package com.resumeai.jobmatch.repository;

import com.resumeai.jobmatch.entity.JobMatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JobMatchRepository extends JpaRepository<JobMatch, Long> {

    List<JobMatch> findByResumeId(Long resumeId);

    List<JobMatch> findByUserId(String userId);

    Optional<JobMatch> findByMatchId(Long matchId);

    List<JobMatch> findByMatchScoreGreaterThanEqual(int score);

    List<JobMatch> findByIsBookmarkedTrueAndUserId(String userId);

    List<JobMatch> findByJobTitleContainingIgnoreCase(String title);

    long countByUserId(String userId);
}
