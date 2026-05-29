package com.resumeai.template.service;

import com.resumeai.template.dto.TemplateCreateRequest;
import com.resumeai.template.dto.TemplateResponseDTO;
import com.resumeai.template.entity.ResumeTemplate;
import com.resumeai.template.repository.TemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TemplateServiceImpl.
 * 
 * Testing Focus:
 *  - Mapping from DTO to Entity and vice versa.
 *  - Proper querying of templates by ID or Template Key.
 *  - Validation that category filters and premium filters work.
 *  - Updating templates selectively.
 */
@ExtendWith(MockitoExtension.class)
class TemplateServiceImplTest {

    @Mock
    private TemplateRepository templateRepository;

    @InjectMocks
    private TemplateServiceImpl templateService;

    private ResumeTemplate sampleTemplate;
    private TemplateCreateRequest createRequest;

    @BeforeEach
    void setUp() {
        sampleTemplate = new ResumeTemplate();
        sampleTemplate.setTemplateId("uuid-123");
        sampleTemplate.setName("Modern Standard");
        sampleTemplate.setTemplateKey("modern-standard");
        sampleTemplate.setCategory("Professional");
        sampleTemplate.setPremium(false);
        sampleTemplate.setActive(true);
        sampleTemplate.setUsageCount(100);

        createRequest = new TemplateCreateRequest();
        createRequest.setName("New Template");
        createRequest.setTemplateKey("new-template");
        createRequest.setPremium(true);
        createRequest.setActive(true);
    }

    @Nested
    @DisplayName("Create & Update")
    class CreateAndUpdateTests {

        @Test
        @DisplayName("createTemplate should save entity and return DTO")
        void createTemplate_success() {
            // ARRANGE
            when(templateRepository.save(any(ResumeTemplate.class))).thenAnswer(inv -> {
                ResumeTemplate t = inv.getArgument(0);
                t.setTemplateId("new-uuid");
                return t;
            });

            // ACT
            TemplateResponseDTO result = templateService.createTemplate(createRequest);

            // ASSERT
            assertEquals("new-uuid", result.getTemplateId());
            assertEquals("New Template", result.getName());
            assertTrue(result.isPremium());
            verify(templateRepository).save(any(ResumeTemplate.class));
        }

        @Test
        @DisplayName("updateTemplate should only update provided fields")
        void updateTemplate_success() {
            // ARRANGE
            when(templateRepository.findById("uuid-123")).thenReturn(Optional.of(sampleTemplate));
            when(templateRepository.save(any(ResumeTemplate.class))).thenAnswer(inv -> inv.getArgument(0));

            TemplateCreateRequest updates = new TemplateCreateRequest();
            updates.setName("Updated Modern"); // Only updating name

            // ACT
            TemplateResponseDTO result = templateService.updateTemplate("uuid-123", updates);

            // ASSERT: Name updated, category unchanged
            assertEquals("Updated Modern", result.getName());
            assertEquals("Professional", result.getCategory());
            assertNotNull(sampleTemplate.getUpdatedAt()); // Timestamp updated
            verify(templateRepository).save(sampleTemplate);
        }

        @Test
        @DisplayName("updateTemplate throws RuntimeException if not found")
        void updateTemplate_notFound() {
            when(templateRepository.findById("uuid-999")).thenReturn(Optional.empty());

            assertThrows(RuntimeException.class, 
                () -> templateService.updateTemplate("uuid-999", new TemplateCreateRequest()));
        }
    }

    @Nested
    @DisplayName("Retrieval Methods")
    class RetrievalTests {

        @Test
        @DisplayName("getTemplateById should find by UUID first")
        void getTemplateById_foundById() {
            when(templateRepository.findById("uuid-123")).thenReturn(Optional.of(sampleTemplate));

            Optional<TemplateResponseDTO> result = templateService.getTemplateById("uuid-123");

            assertTrue(result.isPresent());
            assertEquals("Modern Standard", result.get().getName());
        }

        @Test
        @DisplayName("getTemplateById should fallback to Template Key if UUID not found")
        void getTemplateById_foundByKey() {
            // ARRANGE: Not found by ID, but found by Key
            when(templateRepository.findById("modern-standard")).thenReturn(Optional.empty());
            when(templateRepository.findByTemplateKey("modern-standard")).thenReturn(Optional.of(sampleTemplate));

            // ACT
            Optional<TemplateResponseDTO> result = templateService.getTemplateById("modern-standard");

            // ASSERT
            assertTrue(result.isPresent());
            assertEquals("Modern Standard", result.get().getName());
            verify(templateRepository).findByTemplateKey("modern-standard");
        }

        @Test
        @DisplayName("List queries should delegate to repository and map to DTOs")
        void listQueries_success() {
            // ARRANGE
            List<ResumeTemplate> templates = List.of(sampleTemplate);

            when(templateRepository.findAll()).thenReturn(templates);
            when(templateRepository.findByIsPremiumAndIsActiveTrue(false)).thenReturn(templates);
            when(templateRepository.findByCategoryAndIsActiveTrue("Professional")).thenReturn(templates);
            when(templateRepository.findByIsActiveTrueOrderByUsageCountDesc()).thenReturn(templates);

            // ACT & ASSERT
            assertEquals(1, templateService.getAllTemplates().size());
            assertEquals(1, templateService.getFreeTemplates().size());
            assertEquals(1, templateService.getByCategory("Professional").size());
            assertEquals(1, templateService.getPopularTemplates().size());
        }
    }

    @Nested
    @DisplayName("Status and Usage Updates")
    class StatusUpdatesTests {

        @Test
        @DisplayName("deactivateTemplate should set active=false")
        void deactivateTemplate_success() {
            when(templateRepository.findById("uuid-123")).thenReturn(Optional.of(sampleTemplate));

            templateService.deactivateTemplate("uuid-123");

            assertFalse(sampleTemplate.isActive());
            verify(templateRepository).save(sampleTemplate);
        }

        @Test
        @DisplayName("incrementUsage should delegate to repository")
        void incrementUsage_success() {
            templateService.incrementUsage("uuid-123");
            verify(templateRepository).incrementUsageCount("uuid-123");
        }
    }
}
