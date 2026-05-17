package com.resumeai.section.repository;

import com.resumeai.section.entity.Section;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface SectionRepository extends JpaRepository<Section, Long> {

    List<Section> findByResumeId(Long resumeId);
    List<Section> findByResumeIdOrderByDisplayOrderAsc(Long resumeId);
    Optional<Section> findBySectionId(Long sectionId);
    List<Section> findByResumeIdAndSectionType(Long resumeId, String sectionType);
    List<Section> findBySectionType(String sectionType);
    List<Section> findByAiGenerated(Boolean aiGenerated);
    List<Section> findByIsVisible(Boolean isVisible);
    List<Section> findByResumeIdAndIsVisible(Long resumeId, Boolean isVisible);

    long countByResumeId(Long resumeId);
    long countByResumeIdAndAiGenerated(Long resumeId, Boolean aiGenerated);

    @Modifying
    @Transactional
    @Query("DELETE FROM Section s WHERE s.resumeId = :resumeId")
    void deleteByResumeId(@Param("resumeId") Long resumeId);

    @Modifying
    @Transactional
    void deleteBySectionId(Long sectionId);

    @Modifying
    @Transactional
    @Query("UPDATE Section s SET s.displayOrder = :order WHERE s.sectionId = :id")
    void updateDisplayOrder(@Param("id") Long id, @Param("order") Integer order);

    @Query("SELECT COALESCE(MAX(s.displayOrder), -1) FROM Section s WHERE s.resumeId = :resumeId")
    Integer findMaxDisplayOrder(@Param("resumeId") Long resumeId);
}