package com.resumeai.section.service;

import com.resumeai.section.entity.Section;
import com.resumeai.section.repository.SectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

/**
 * Unit tests for SectionServiceImpl.
 * 
 * Tests the CRUD operations for Resume Sections (Experience, Education, etc.).
 * Includes specific tests for display order calculation, bulk updates, and visibility toggling.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SectionServiceImplTest {

    @Mock
    private SectionRepository sectionRepository;

    @InjectMocks
    private SectionServiceImpl sectionService;

    private Section sampleSection;

    @BeforeEach
    void setUp() {
        sampleSection = new Section();
        sampleSection.setSectionId(1L);
        sampleSection.setResumeId(100L);
        sampleSection.setTitle("Work Experience");
        sampleSection.setSectionType("EXPERIENCE");
    }

    // ========================================================================
    //  addSection() Tests
    // ========================================================================
    @Nested
    @DisplayName("addSection()")
    class AddSectionTests {

        @Test
        @DisplayName("Should assign display order as max + 1 when order is null")
        void addSection_calculatesDisplayOrder() {
            // ARRANGE: Max order in DB is 2
            sampleSection.setDisplayOrder(-1); // Force auto-calculation
            when(sectionRepository.findMaxDisplayOrder(100L)).thenReturn(2);
            when(sectionRepository.save(any(Section.class))).thenAnswer(inv -> inv.getArgument(0));

            // ACT
            Section result = sectionService.addSection(sampleSection);

            // ASSERT: Display order should be 2 + 1 = 3
            assertEquals(3, result.getDisplayOrder());
            // Defaults should be set
            assertTrue(result.getIsVisible());
            assertFalse(result.getAiGenerated());
            verify(sectionRepository).save(sampleSection);
        }

        @Test
        @DisplayName("Should assign display order as 0 when no sections exist")
        void addSection_firstSection_orderZero() {
            // ARRANGE: No sections exist (returns null)
            sampleSection.setDisplayOrder(-1); // Force auto-calculation
            when(sectionRepository.findMaxDisplayOrder(100L)).thenReturn(null);
            when(sectionRepository.save(any(Section.class))).thenAnswer(inv -> inv.getArgument(0));

            // ACT
            Section result = sectionService.addSection(sampleSection);

            // ASSERT: Display order starts at 0
            assertEquals(0, result.getDisplayOrder());
        }

        @Test
        @DisplayName("Should keep existing display order if provided")
        void addSection_keepsExistingOrder() {
            sampleSection.setDisplayOrder(5);
            when(sectionRepository.save(any(Section.class))).thenAnswer(inv -> inv.getArgument(0));

            Section result = sectionService.addSection(sampleSection);

            // ASSERT: Retains the manually set order
            assertEquals(5, result.getDisplayOrder());
            verify(sectionRepository, never()).findMaxDisplayOrder(anyLong());
        }
    }

    // ========================================================================
    //  updateSection() & toggleVisibility() Tests
    // ========================================================================
    @Nested
    @DisplayName("Updates and Visibility")
    class UpdateTests {

        @Test
        @DisplayName("Should update only provided fields")
        void updateSection_success() {
            // ARRANGE
            sampleSection.setContent("Old Content");
            when(sectionRepository.findById(1L)).thenReturn(Optional.of(sampleSection));
            when(sectionRepository.save(any(Section.class))).thenAnswer(inv -> inv.getArgument(0));

            Section updates = new Section();
            updates.setContent("New Content");
            
            // ACT
            Section result = sectionService.updateSection(1L, updates);

            // ASSERT
            assertEquals("New Content", result.getContent());
            assertEquals("Work Experience", result.getTitle()); // Unchanged
        }

        @Test
        @DisplayName("Should throw RuntimeException if section not found")
        void updateSection_notFound() {
            when(sectionRepository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(RuntimeException.class, () -> sectionService.updateSection(99L, new Section()));
        }

        @Test
        @DisplayName("Should toggle visibility from true to false")
        void toggleVisibility_trueToFalse() {
            sampleSection.setIsVisible(true);
            when(sectionRepository.findById(1L)).thenReturn(Optional.of(sampleSection));
            when(sectionRepository.save(any(Section.class))).thenAnswer(inv -> inv.getArgument(0));

            Section result = sectionService.toggleVisibility(1L);

            assertFalse(result.getIsVisible());
        }
    }

    // ========================================================================
    //  List Operations (Reorder, Bulk Update) Tests
    // ========================================================================
    @Nested
    @DisplayName("Bulk Operations")
    class BulkOperationsTests {

        @Test
        @DisplayName("reorderSections should update display order for all provided items")
        void reorderSections_success() {
            // ARRANGE
            Section s1 = new Section(); s1.setSectionId(1L); s1.setDisplayOrder(1);
            Section s2 = new Section(); s2.setSectionId(2L); s2.setDisplayOrder(2);
            
            when(sectionRepository.findByResumeIdOrderByDisplayOrderAsc(100L)).thenReturn(Arrays.asList(s1, s2));

            // ACT
            List<Section> results = sectionService.reorderSections(100L, Arrays.asList(s1, s2));

            // ASSERT & VERIFY
            verify(sectionRepository).updateDisplayOrder(1L, 1);
            verify(sectionRepository).updateDisplayOrder(2L, 2);
            assertEquals(2, results.size());
        }

        @Test
        @DisplayName("bulkUpdateSections should save existing items with new data")
        void bulkUpdateSections_success() {
            // ARRANGE
            Section existing = new Section();
            existing.setSectionId(1L);
            when(sectionRepository.findById(1L)).thenReturn(Optional.of(existing));

            Section updateReq = new Section();
            updateReq.setSectionId(1L);
            updateReq.setTitle("Updated Title");

            when(sectionRepository.findByResumeIdOrderByDisplayOrderAsc(100L)).thenReturn(List.of(existing));

            // ACT
            sectionService.bulkUpdateSections(100L, List.of(updateReq));

            // VERIFY
            verify(sectionRepository).save(argThat(s -> "Updated Title".equals(s.getTitle())));
        }
    }
}
