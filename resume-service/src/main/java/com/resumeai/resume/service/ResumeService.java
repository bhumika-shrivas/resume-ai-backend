package com.resumeai.resume.service;

import com.resumeai.resume.entity.Resume;
import java.util.List;

public interface ResumeService {
    Resume createResume(Resume resume, String userEmail);
    Resume getResumeById(Long id, String userEmail);
    List<Resume> getResumesByUser(String userEmail);
    Resume updateResume(Long id, Resume resume, String userEmail);
    void deleteResume(Long id, String userEmail);
    Resume duplicateResume(Long id, String userEmail);
    Resume updateAtsScore(Long id, Integer score, String userEmail);

    List<Resume> getPublicResumes();
    Resume incrementViewCount(Long id);
    List<Resume> getResumesByTemplate(String templateId);
    long countUserResumes(String userEmail);
    long countAllResumes();
}