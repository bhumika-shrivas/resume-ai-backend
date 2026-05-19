package com.resumeai.template.repository;

import com.resumeai.template.entity.ResumeTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface TemplateRepository extends JpaRepository<ResumeTemplate, String> {

    List<ResumeTemplate> findByCategoryAndIsActiveTrue(String category);

    List<ResumeTemplate> findByIsPremiumAndIsActiveTrue(boolean isPremium);

    List<ResumeTemplate> findByIsActiveTrue();

    List<ResumeTemplate> findByIsActiveTrueOrderByUsageCountDesc();

    Optional<ResumeTemplate> findByTemplateKey(String templateKey);

    @Modifying
    @Transactional
    @Query("UPDATE ResumeTemplate t SET t.usageCount = t.usageCount + 1 WHERE t.templateId = :templateId OR t.templateKey = :templateId")
    void incrementUsageCount(@Param("templateId") String templateId);

}
