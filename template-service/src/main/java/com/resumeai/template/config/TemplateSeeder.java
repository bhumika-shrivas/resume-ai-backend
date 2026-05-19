package com.resumeai.template.config;

import com.resumeai.template.entity.ResumeTemplate;
import com.resumeai.template.repository.TemplateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Component
public class TemplateSeeder implements CommandLineRunner {

    @Autowired
    private TemplateRepository templateRepository;

    @Override
    public void run(String... args) throws Exception {
        if (templateRepository.findByIsActiveTrue().isEmpty()) {
            System.out.println("No active templates found. Cleaning up and re-seeding templates...");
            templateRepository.deleteAll();

            List<ResumeTemplate> initialTemplates = Arrays.asList(
                    ResumeTemplate.builder()
                            .name("Minimalist Clean")
                            .description("A clean, modern, and highly scannable design perfect for corporate roles.")
                            .templateKey("minimal-template")
                            .category("Minimalist")
                            .primaryColor("#1e293b")
                            .layoutType("SINGLE_COLUMN")
                            .isPremium(false)
                            .isActive(true)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build(),

                    ResumeTemplate.builder()
                            .name("Modern Executive")
                            .description("A sharp, two-column layout tailored for leadership and management positions.")
                            .templateKey("modern-template")
                            .category("Professional")
                            .primaryColor("#0f172a")
                            .layoutType("TWO_COLUMN")
                            .isPremium(true)
                            .isActive(true)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build(),

                    ResumeTemplate.builder()
                            .name("Creative Professional")
                            .description("Stand out with bold typography and a unique layout for creative roles.")
                            .templateKey("creative-template")
                            .category("Creative")
                            .primaryColor("#ec4899")
                            .layoutType("TWO_COLUMN")
                            .isPremium(true)
                            .isActive(true)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build(),

                    ResumeTemplate.builder()
                            .name("ATS Optimized")
                            .description("A simple, standard format guaranteed to pass through Applicant Tracking Systems.")
                            .templateKey("ats-template")
                            .category("ATS")
                            .primaryColor("#000000")
                            .layoutType("SINGLE_COLUMN")
                            .isPremium(false)
                            .isActive(true)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build(),

                    ResumeTemplate.builder()
                            .name("Executive Professional")
                            .description("A premium layout for senior professionals.")
                            .templateKey("executive-template")
                            .category("Professional")
                            .primaryColor("#1d4ed8")
                            .layoutType("TWO_COLUMN")
                            .isPremium(true)
                            .isActive(true)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build()
            );

            templateRepository.saveAll(initialTemplates);
            System.out.println("Seeded " + initialTemplates.size() + " templates successfully.");
        }
    }
}
