package com.resumeai.export.repository;

import com.resumeai.export.entity.ExportJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExportRepository extends JpaRepository<ExportJob, String> {

    Optional<ExportJob> findByJobId(String jobId);

    List<ExportJob> findByUserId(String userId);

    List<ExportJob> findByResumeId(Long resumeId);

    List<ExportJob> findByStatus(String status);

    List<ExportJob> findByFormat(String format);

    @Query("SELECT e FROM ExportJob e WHERE e.expiresAt < :now")
    List<ExportJob> findExpiredJobs(LocalDateTime now);

    @Query("SELECT COUNT(e) FROM ExportJob e WHERE e.userId = :userId AND e.requestedAt > :startOfDay")
    int countByUserIdToday(String userId, LocalDateTime startOfDay);
}
