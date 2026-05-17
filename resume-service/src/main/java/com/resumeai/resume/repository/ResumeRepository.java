package com.resumeai.resume.repository;

import com.resumeai.resume.entity.Resume;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ResumeRepository extends JpaRepository<Resume, Long> {

    List<Resume> findByUserEmail(String userEmail);

    Optional<Resume> findByIdAndUserEmail(Long id, String userEmail);

    List<Resume> findByStatus(String status);

    List<Resume> findByUserEmailAndStatus(String userEmail, String status);

    List<Resume> findByTargetJobTitle(String targetJobTitle);

    List<Resume> findByIsPublicTrue();

    long countByUserEmail(String userEmail);

    List<Resume> findByTemplateId(String templateId);

    void deleteByIdAndUserEmail(Long id, String userEmail);
}