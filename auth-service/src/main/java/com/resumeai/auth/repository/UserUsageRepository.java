package com.resumeai.auth.repository;

import com.resumeai.auth.entity.UserUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface UserUsageRepository extends JpaRepository<UserUsage, Long> {
    @Query("SELECT COALESCE(SUM(u.aiCallsThisMonth), 0L) FROM UserUsage u")
    Long getTotalAiCalls();

    @Query("SELECT COALESCE(SUM(u.atsChecksThisMonth), 0L) FROM UserUsage u")
    Long getTotalAtsChecks();
}
