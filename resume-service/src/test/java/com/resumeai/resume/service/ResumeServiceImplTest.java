package com.resumeai.resume.service;

import com.resumeai.resume.client.AuthClient;
import com.resumeai.resume.client.NotificationClient;
import com.resumeai.resume.client.TemplateClient;
import com.resumeai.resume.client.TemplateDto;
import com.resumeai.resume.client.UserDto;
import com.resumeai.resume.entity.Resume;
import com.resumeai.resume.repository.ResumeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ResumeServiceImpl.
 *
 * Tests the core logic for managing resumes:
 *  - createResume(): Handles free tier limits (3 max) and premium template checks.
 *  - updateResume(): Allows updating fields and re-validates template changes.
 *  - duplicateResume(): Copies resume data but resets counters (ATS score, view count).
 *  - updateAtsScore(): Updates score and sends a notification.
 *  - Standard CRUD and query operations.
 *
 * MOCK SETUP:
 *  - We mock all database calls (ResumeRepository) and external microservice calls 
 *    (AuthClient, TemplateClient, NotificationClient).
 */
@ExtendWith(MockitoExtension.class)
class ResumeServiceImplTest {

    @Mock
    private ResumeRepository resumeRepository;

    @Mock
    private NotificationClient notificationClient;

    @Mock
    private AuthClient authClient;

    @Mock
    private TemplateClient templateClient;

    @InjectMocks
    private ResumeServiceImpl resumeService;

    private UserDto freeUser;
    private UserDto premiumUser;
    private TemplateDto freeTemplate;
    private TemplateDto premiumTemplate;
    private Resume sampleResume;

    @BeforeEach
    void setUp() {
        freeUser = new UserDto();
        freeUser.setEmail("free@test.com");
        freeUser.setSubscriptionPlan("FREE");

        premiumUser = new UserDto();
        premiumUser.setEmail("premium@test.com");
        premiumUser.setSubscriptionPlan("PREMIUM");

        freeTemplate = new TemplateDto();
        freeTemplate.setTemplateId("temp-free");
        freeTemplate.setPremium(false);

        premiumTemplate = new TemplateDto();
        premiumTemplate.setTemplateId("temp-prem");
        premiumTemplate.setPremium(true);

        sampleResume = new Resume();
        sampleResume.setId(10L);
        sampleResume.setUserEmail("free@test.com");
        sampleResume.setTitle("My Resume");
        sampleResume.setStatus("DRAFT");
    }

    // ========================================================================
    //  createResume() Tests
    // ========================================================================
    @Nested
    @DisplayName("createResume()")
    class CreateResumeTests {

        @Test
        @DisplayName("Should create resume for FREE user if under limit and using free template")
        void createResume_freeUser_success() {
            // ARRANGE
            when(authClient.getUserByEmail("free@test.com")).thenReturn(freeUser);
            when(resumeRepository.countByUserEmail("free@test.com")).thenReturn(2L); // 2 < 3

            Resume newResume = new Resume();
            newResume.setTemplateId("temp-free");
            when(templateClient.getTemplateById("temp-free")).thenReturn(freeTemplate);
            when(resumeRepository.save(any(Resume.class))).thenAnswer(inv -> inv.getArgument(0));

            // ACT
            Resume created = resumeService.createResume(newResume, "free@test.com");

            // ASSERT: Defaults set correctly
            assertEquals("DRAFT", created.getStatus());
            assertEquals(0, created.getAtsScore());
            assertEquals(0, created.getViewCount());
            assertFalse(created.getIsPublic());
            verify(resumeRepository).save(newResume);
        }

        @Test
        @DisplayName("Should throw exception if FREE user has reached 3 resume limit")
        void createResume_freeUser_limitReached() {
            // ARRANGE
            when(authClient.getUserByEmail("free@test.com")).thenReturn(freeUser);
            when(resumeRepository.countByUserEmail("free@test.com")).thenReturn(3L); // Limit reached

            // ACT & ASSERT
            RuntimeException ex = assertThrows(RuntimeException.class, 
                () -> resumeService.createResume(new Resume(), "free@test.com"));
            assertTrue(ex.getMessage().contains("limited to 3 resumes"));

            verify(resumeRepository, never()).save(any(Resume.class));
        }

        @Test
        @DisplayName("Should allow PREMIUM user to bypass 3 resume limit")
        void createResume_premiumUser_bypassesLimit() {
            // ARRANGE
            when(authClient.getUserByEmail("premium@test.com")).thenReturn(premiumUser);
            // We don't even stub countByUserEmail because premium users shouldn't trigger the count check

            Resume newResume = new Resume();
            when(resumeRepository.save(any(Resume.class))).thenAnswer(inv -> inv.getArgument(0));

            // ACT
            assertDoesNotThrow(() -> resumeService.createResume(newResume, "premium@test.com"));

            // VERIFY
            verify(resumeRepository, never()).countByUserEmail(anyString());
            verify(resumeRepository).save(newResume);
        }

        @Test
        @DisplayName("Should block FREE user from using a PREMIUM template")
        void createResume_freeUser_premiumTemplateBlocked() {
            // ARRANGE
            when(authClient.getUserByEmail("free@test.com")).thenReturn(freeUser);
            when(resumeRepository.countByUserEmail("free@test.com")).thenReturn(0L);
            
            Resume newResume = new Resume();
            newResume.setTemplateId("temp-prem");
            when(templateClient.getTemplateById("temp-prem")).thenReturn(premiumTemplate);

            // ACT & ASSERT
            RuntimeException ex = assertThrows(RuntimeException.class, 
                () -> resumeService.createResume(newResume, "free@test.com"));
            assertTrue(ex.getMessage().contains("Premium members only"));

            verify(resumeRepository, never()).save(any(Resume.class));
        }

        @Test
        @DisplayName("Should allow creation gracefully if auth-service fails (fail-open)")
        void createResume_authServiceFails_defaultsToFree() {
            // ARRANGE
            when(authClient.getUserByEmail("free@test.com")).thenThrow(new RuntimeException("Service down"));
            // Since it defaults to free, it will check the count
            when(resumeRepository.countByUserEmail("free@test.com")).thenReturn(1L);

            Resume newResume = new Resume();
            when(resumeRepository.save(any(Resume.class))).thenAnswer(inv -> inv.getArgument(0));

            // ACT & ASSERT
            assertDoesNotThrow(() -> resumeService.createResume(newResume, "free@test.com"));
            verify(resumeRepository).save(newResume);
        }
    }

    // ========================================================================
    //  updateResume() Tests
    // ========================================================================
    @Nested
    @DisplayName("updateResume()")
    class UpdateResumeTests {

        @Test
        @DisplayName("Should update fields and save")
        void updateResume_success() {
            // ARRANGE
            when(resumeRepository.findByIdAndUserEmail(10L, "free@test.com")).thenReturn(Optional.of(sampleResume));
            
            Resume updates = new Resume();
            updates.setTitle("New Title");
            updates.setSummary("New Summary");
            
            when(resumeRepository.save(any(Resume.class))).thenAnswer(inv -> inv.getArgument(0));

            // ACT
            Resume updated = resumeService.updateResume(10L, updates, "free@test.com");

            // ASSERT
            assertEquals("New Title", updated.getTitle());
            assertEquals("New Summary", updated.getSummary());
            verify(resumeRepository).save(sampleResume);
        }

        @Test
        @DisplayName("Should block template update if FREE user chooses PREMIUM template")
        void updateResume_premiumTemplateBlockedForFreeUser() {
            // ARRANGE
            when(resumeRepository.findByIdAndUserEmail(10L, "free@test.com")).thenReturn(Optional.of(sampleResume));
            when(authClient.getUserByEmail("free@test.com")).thenReturn(freeUser);
            when(templateClient.getTemplateById("temp-prem")).thenReturn(premiumTemplate);

            Resume updates = new Resume();
            updates.setTemplateId("temp-prem");

            // ACT & ASSERT
            RuntimeException ex = assertThrows(RuntimeException.class, 
                () -> resumeService.updateResume(10L, updates, "free@test.com"));
            assertTrue(ex.getMessage().contains("Premium members only"));

            verify(resumeRepository, never()).save(any(Resume.class));
        }
    }

    // ========================================================================
    //  duplicateResume() Tests
    // ========================================================================
    @Nested
    @DisplayName("duplicateResume()")
    class DuplicateResumeTests {

        @Test
        @DisplayName("Should copy resume and reset metrics/stats")
        void duplicateResume_success() {
            // ARRANGE
            sampleResume.setAtsScore(85);
            sampleResume.setViewCount(100);
            sampleResume.setIsPublic(true);
            when(resumeRepository.findByIdAndUserEmail(10L, "free@test.com")).thenReturn(Optional.of(sampleResume));
            when(resumeRepository.save(any(Resume.class))).thenAnswer(inv -> inv.getArgument(0));

            // ACT
            Resume copy = resumeService.duplicateResume(10L, "free@test.com");

            // ASSERT: Copy gets "Copy of" prefix and resets metrics
            assertEquals("Copy of My Resume", copy.getTitle());
            assertEquals("DRAFT", copy.getStatus());
            assertEquals(0, copy.getAtsScore());
            assertEquals(0, copy.getViewCount());
            assertFalse(copy.getIsPublic());
            
            verify(resumeRepository).save(any(Resume.class));
        }
    }

    // ========================================================================
    //  updateAtsScore() Tests
    // ========================================================================
    @Nested
    @DisplayName("updateAtsScore()")
    class UpdateAtsScoreTests {

        @Test
        @DisplayName("Should update score and send async notification")
        void updateAtsScore_success() {
            // ARRANGE
            when(resumeRepository.findByIdAndUserEmail(10L, "free@test.com")).thenReturn(Optional.of(sampleResume));
            when(resumeRepository.save(any(Resume.class))).thenAnswer(inv -> inv.getArgument(0));

            // ACT
            Resume updated = resumeService.updateAtsScore(10L, 95, "free@test.com");

            // ASSERT
            assertEquals(95, updated.getAtsScore());
            verify(resumeRepository).save(sampleResume);
            verify(notificationClient).sendNotification(anyMap());
        }

        @Test
        @DisplayName("Should gracefully handle notification failure")
        void updateAtsScore_notificationFails() {
            // ARRANGE
            when(resumeRepository.findByIdAndUserEmail(10L, "free@test.com")).thenReturn(Optional.of(sampleResume));
            when(resumeRepository.save(any(Resume.class))).thenAnswer(inv -> inv.getArgument(0));
            doThrow(new RuntimeException("Notification service down")).when(notificationClient).sendNotification(anyMap());

            // ACT & ASSERT: Exception swallowed, save still happens
            assertDoesNotThrow(() -> resumeService.updateAtsScore(10L, 95, "free@test.com"));
            assertEquals(95, sampleResume.getAtsScore());
        }
    }
    
    // ========================================================================
    //  CRUD & Metric Queries Tests
    // ========================================================================
    @Test
    @DisplayName("getResumeById should throw if not found")
    void getResumeById_notFound() {
        when(resumeRepository.findByIdAndUserEmail(99L, "free@test.com")).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> resumeService.getResumeById(99L, "free@test.com"));
    }

    @Test
    @DisplayName("deleteResume should delete existing resume")
    void deleteResume_success() {
        when(resumeRepository.findByIdAndUserEmail(10L, "free@test.com")).thenReturn(Optional.of(sampleResume));
        resumeService.deleteResume(10L, "free@test.com");
        verify(resumeRepository).delete(sampleResume);
    }

    @Test
    @DisplayName("incrementViewCount should add 1 to view count")
    void incrementViewCount_success() {
        sampleResume.setViewCount(5);
        when(resumeRepository.findById(10L)).thenReturn(Optional.of(sampleResume));
        when(resumeRepository.save(any(Resume.class))).thenAnswer(inv -> inv.getArgument(0));

        Resume updated = resumeService.incrementViewCount(10L);
        assertEquals(6, updated.getViewCount());
    }

    @Test
    @DisplayName("Various list/count methods should delegate to repository")
    void listAndCountMethods() {
        when(resumeRepository.findByUserEmail("free@test.com")).thenReturn(List.of(sampleResume));
        assertEquals(1, resumeService.getResumesByUser("free@test.com").size());

        when(resumeRepository.findByIsPublicTrue()).thenReturn(List.of(sampleResume));
        assertEquals(1, resumeService.getPublicResumes().size());

        when(resumeRepository.findByTemplateId("temp-1")).thenReturn(List.of(sampleResume));
        assertEquals(1, resumeService.getResumesByTemplate("temp-1").size());

        when(resumeRepository.countByUserEmail("free@test.com")).thenReturn(42L);
        assertEquals(42L, resumeService.countUserResumes("free@test.com"));

        when(resumeRepository.count()).thenReturn(100L);
        assertEquals(100L, resumeService.countAllResumes());
    }
}
