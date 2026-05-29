package com.resumeai.template.repository;

import com.resumeai.template.entity.TemplateUsageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface TemplateUsageLogRepository extends JpaRepository<TemplateUsageLog, Long> {

    @Query("SELECT COUNT(l) FROM TemplateUsageLog l WHERE l.templateId = :templateId AND l.usedAt BETWEEN :startDate AND :endDate")
    long countUsageBetween(@Param("templateId") String templateId, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
}
