package com.resumeai.section.service;

import com.resumeai.section.dto.ResumeData;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SectionAggregatorService.
 *
 * This service takes multiple unstructured database rows (JSON strings in content)
 * and aggregates them into a strongly-typed `ResumeData` object.
 *
 * Testing Focus:
 *  - Ensuring JSON parsing handles valid maps and lists properly.
 *  - Ensuring hidden sections are ignored.
 *  - Ensuring parsing failures don't crash the entire aggregation (graceful degradation).
 *  - Ensuring collections are never null in the final object.
 */
@ExtendWith(MockitoExtension.class)
class SectionAggregatorServiceTest {

    @Mock
    private SectionRepository sectionRepository;

    @InjectMocks
    private SectionAggregatorService aggregatorService;

    private Section personalInfoSec;
    private Section experienceSec;
    private Section skillsSec;
    private Section hiddenSec;
    private Section invalidSec;

    @BeforeEach
    void setUp() {
        personalInfoSec = new Section();
        personalInfoSec.setSectionType("PERSONAL_INFO");
        personalInfoSec.setIsVisible(true);
        personalInfoSec.setContent("{\"name\": \"John Doe\", \"email\": \"john@test.com\"}");

        experienceSec = new Section();
        experienceSec.setSectionType("EXPERIENCE");
        experienceSec.setIsVisible(true);
        experienceSec.setContent("[{\"company\": \"Google\"}, {\"company\": \"Apple\"}]");

        skillsSec = new Section();
        skillsSec.setSectionType("SKILLS");
        skillsSec.setIsVisible(true);
        skillsSec.setContent("[\"Java\", \"Python\"]");

        hiddenSec = new Section();
        hiddenSec.setSectionType("EDUCATION");
        hiddenSec.setIsVisible(false); // Should be ignored
        hiddenSec.setContent("[{\"school\": \"MIT\"}]");

        invalidSec = new Section();
        invalidSec.setSectionType("CERTIFICATIONS");
        invalidSec.setIsVisible(true);
        invalidSec.setContent("{INVALID_JSON]"); // Should fail gracefully
    }

    @Nested
    @DisplayName("aggregateResume()")
    class AggregateResumeTests {

        @Test
        @DisplayName("Should successfully parse and aggregate valid sections")
        void aggregateResume_success() {
            // ARRANGE
            when(sectionRepository.findByResumeIdOrderByDisplayOrderAsc(100L))
                .thenReturn(Arrays.asList(personalInfoSec, experienceSec, skillsSec, hiddenSec));

            // ACT
            ResumeData data = aggregatorService.aggregateResume(100L);

            // ASSERT: Personal Info parsed to map
            assertEquals(2, data.getPersonalInfo().size());
            assertEquals("John Doe", data.getPersonalInfo().get("name"));

            // ASSERT: Experience parsed to list
            assertEquals(2, data.getExperience().size());
            assertEquals("Google", data.getExperience().get(0).get("company"));

            // ASSERT: Skills parsed to list of strings
            assertEquals(2, data.getSkills().size());
            assertEquals("Java", data.getSkills().get(0));

            // ASSERT: Education is EMPTY because the section was hidden
            assertTrue(data.getEducation().isEmpty());
        }

        @Test
        @DisplayName("Should gracefully handle invalid JSON without crashing")
        void aggregateResume_invalidJson_gracefulSkip() {
            // ARRANGE
            when(sectionRepository.findByResumeIdOrderByDisplayOrderAsc(100L))
                .thenReturn(List.of(personalInfoSec, invalidSec));

            // ACT
            ResumeData data = aggregatorService.aggregateResume(100L);

            // ASSERT: Personal info still parsed successfully
            assertEquals("John Doe", data.getPersonalInfo().get("name"));
            
            // ASSERT: Certifications (invalid JSON) is empty list, not null, no exception thrown
            assertNotNull(data.getCertifications());
            assertTrue(data.getCertifications().isEmpty());
        }

        @Test
        @DisplayName("Should parse complex skills JSON format")
        void aggregateResume_complexSkills() {
            // ARRANGE: Sometimes skills come as objects instead of just strings
            Section complexSkills = new Section();
            complexSkills.setSectionType("SKILLS");
            complexSkills.setIsVisible(true);
            complexSkills.setContent("[{\"name\": \"Java\", \"level\": \"Expert\"}]");

            when(sectionRepository.findByResumeIdOrderByDisplayOrderAsc(100L))
                .thenReturn(List.of(complexSkills));

            // ACT
            ResumeData data = aggregatorService.aggregateResume(100L);

            // ASSERT: Should extract the "name" field from the object
            assertEquals(1, data.getSkills().size());
            assertEquals("Java", data.getSkills().get(0));
        }

        @Test
        @DisplayName("Should handle missing or empty sections by defaulting to empty collections")
        void aggregateResume_emptyResume_defaultsSet() {
            // ARRANGE
            when(sectionRepository.findByResumeIdOrderByDisplayOrderAsc(100L)).thenReturn(List.of());

            // ACT
            ResumeData data = aggregatorService.aggregateResume(100L);

            // ASSERT: None of the collections should be null to avoid NullPointerExceptions in frontend/exporters
            assertNotNull(data.getPersonalInfo());
            assertNotNull(data.getExperience());
            assertNotNull(data.getEducation());
            assertNotNull(data.getSkills());
            assertNotNull(data.getProjects());
            assertNotNull(data.getCertifications());
            assertNull(data.getSummary()); // Strings can be null
        }
    }
}
