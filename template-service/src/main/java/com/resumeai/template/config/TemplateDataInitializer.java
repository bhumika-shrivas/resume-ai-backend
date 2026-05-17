package com.resumeai.template.config;

import com.resumeai.template.entity.ResumeTemplate;
import com.resumeai.template.repository.TemplateRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class TemplateDataInitializer implements CommandLineRunner {

    private final TemplateRepository repo;

    public TemplateDataInitializer(TemplateRepository repo) {
        this.repo = repo;
    }

    @Override
    public void run(String... args) {
        if (repo.count() > 0) {
            System.out.println("⚠️ Template database already seeded. Dropping existing to apply corrected metadata...");
            repo.deleteAll();
        }

        repo.saveAll(List.of(
            buildTemplate(
                "modern-template",
                "Modern Professional",
                "A sleek two-column layout with a dark navy sidebar, accent colors, and clean typography. Perfect for tech, engineering, and senior roles.",
                "Professional",
                false,
                "/assets/templates/modern-preview.png",
                "/assets/templates/modern-preview.png",
                "#2563eb",
                "#1e293b",
                "Inter",
                "TWO_COLUMN"
            ),
            buildTemplate(
                "minimal-template",
                "Minimal Clean",
                "Ultra-minimalist single-column layout with elegant whitespace. ATS-optimised, zero clutter — lets your content shine.",
                "Minimalist",
                false,
                "/assets/templates/minimal-preview.png",
                "/assets/templates/minimal-preview.png",
                "#16a34a",
                "#f0fdf4",
                "Inter",
                "SINGLE_COLUMN"
            ),
            buildTemplate(
                "executive-template",
                "Executive",
                "Bold teal header with structured two-column grid. Built for leadership, C-suite, and senior management positions.",
                "Professional",
                false,
                "/assets/templates/executive-preview.png",
                "/assets/templates/executive-preview.png",
                "#0891b2",
                "#e0f2fe",
                "Inter",
                "TWO_COLUMN"
            ),
            buildTemplate(
                "creative-template",
                "Creative Design",
                "Vibrant gradient sidebar with skill bars, avatar placeholder, and card-based sections. Made for designers, artists, and creatives.",
                "Creative",
                true,
                "/assets/templates/creative-preview.png",
                "/assets/templates/creative-preview.png",
                "#7c3aed",
                "#4f46e5",
                "Inter",
                "SIDEBAR"
            ),
            buildTemplate(
                "ats-template",
                "ATS Optimized",
                "Traditional single-column serif layout with zero graphics. Parses perfectly through every applicant tracking system.",
                "ATS",
                false,
                "/assets/templates/ats-preview.png",
                "/assets/templates/ats-preview.png",
                "#7f1d1d",
                "#fef2f2",
                "Georgia",
                "SINGLE_COLUMN"
            )
        ));

        System.out.println("✅ Template database seeded with 5 templates (keys aligned to frontend registry).");
    }

    private ResumeTemplate buildTemplate(String templateKey, String name, String description,
                                          String category, boolean premium,
                                          String thumbnail, String previewImage,
                                          String primaryColor,
                                          String secondaryColor, String fontFamily, String layoutType) {
        ResumeTemplate t = new ResumeTemplate();
        t.setTemplateId(UUID.randomUUID().toString());
        t.setTemplateKey(templateKey);
        t.setName(name);
        t.setDescription(description);
        t.setCategory(category);
        t.setPremium(premium);
        t.setActive(true);
        t.setThumbnailUrl(thumbnail);
        t.setPreviewImageUrl(previewImage);
        t.setPrimaryColor(primaryColor);
        t.setSecondaryColor(secondaryColor);
        t.setFontFamily(fontFamily);
        t.setLayoutType(layoutType);
        t.setUsageCount(0);
        return t;
    }
}
