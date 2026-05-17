package com.resumeai.auth.repository;

import com.resumeai.auth.entity.UserUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface UserUsageRepository extends JpaRepository<UserUsage, Long> {
    
    @Query("SELECT SUM(u.aiCallsThisMonth) FROM UserUsage u")
    Long getTotalAiCalls();

    @Query("SELECT SUM(u.atsChecksThisMonth) FROM UserUsage u")
    Long getTotalAtsChecks();
}
