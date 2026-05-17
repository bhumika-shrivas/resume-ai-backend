package com.resumeai.resume.repository;

import com.resumeai.resume.entity.ResumeSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SectionRepository extends JpaRepository<ResumeSection, Long> {

    List<ResumeSection> findByResumeIdOrderByOrderIndexAsc(Long resumeId);

    void deleteByResumeId(Long resumeId);

    @Modifying
    @Query("UPDATE ResumeSection s SET s.orderIndex = :orderIndex WHERE s.id = :id")
    void updateOrderIndex(Long id, Integer orderIndex);
}
