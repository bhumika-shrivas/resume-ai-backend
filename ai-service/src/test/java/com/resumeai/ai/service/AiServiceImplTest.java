package com.resumeai.ai.service;

import com.resumeai.ai.client.AuthClient;
import com.resumeai.ai.client.GeminiClient;
import com.resumeai.ai.client.NotificationClient;
import com.resumeai.ai.client.ResumeClient;
import com.resumeai.ai.client.UserDto;
import com.resumeai.ai.entity.AiRequest;
import com.resumeai.ai.repository.AiRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AiServiceImpl.
 *
 * WHAT WE TEST:
 *   - generateSummary()    → calls Gemini API, returns AI response, handles fallback on failure
 *   - generateBulletPoints() → calls Gemini JSON mode, parses bullet points
 *   - generateCoverLetter() → premium-only check, calls Gemini
 *   - improveSection()      → premium-only check, calls Gemini
 *   - suggestSkills()       → quota check, calls Gemini JSON mode
 *   - getAiHistory()        → fetches request history from DB
 *   - getRemainingQuota()   → calculates remaining free tier quota
 *
 * HOW WE TEST:
 *   - We mock all external dependencies (Feign clients, Gemini API, database)
 *   - We use @ExtendWith(MockitoExtension.class) so NO Spring context is loaded (fast tests)
 *   - Each test uses when().thenReturn() to set up mock behavior
 *   - verify() confirms the right interactions happened
 */
@ExtendWith(MockitoExtension.class)
class AiServiceImplTest {

    // ── MOCKS ─────────────────────────────────────────────────────────────
    // These are fake versions of the real dependencies.
    // Mockito creates empty stubs that do nothing by default.

    @Mock
    private AuthClient authClient;              // Feign client to auth-service (gets user plan)

    @Mock
    private NotificationClient notificationClient; // Feign client to notification-service

    @Mock
    private ResumeClient resumeClient;          // Feign client to resume-service (gets resume data)

    @Mock
    private GeminiClient geminiClient;          // Calls Google Gemini AI API

    @Mock
    private AiRequestRepository aiRequestRepository; // Database access for AI request logs

    // The class under test — Mockito injects all @Mock fields into it
    @InjectMocks
    private AiServiceImpl aiService;

    // ── SHARED TEST DATA ─────────────────────────────────────────────────
    private UserDto premiumUser;
    private UserDto freeUser;

    @BeforeEach
    void setUp() {
        // Create a PREMIUM user (unlimited AI access)
        premiumUser = new UserDto();
        premiumUser.setEmail("premium@test.com");
        premiumUser.setSubscriptionPlan("PREMIUM");
        premiumUser.setRole("USER");

        // Create a FREE user (limited to 5 AI calls/month)
        freeUser = new UserDto();
        freeUser.setEmail("free@test.com");
        freeUser.setSubscriptionPlan("FREE");
        freeUser.setRole("USER");
    }

    // ===================================================================
    //  Helper: makes the mock repository return a saved AiRequest as-is
    // ===================================================================
    private void stubSaveRequest() {
        // When aiRequestRepository.save() is called, just return whatever was passed in
        when(aiRequestRepository.save(any(AiRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // ===================================================================
    //  Helper: makes the auth client return a given user for any email
    // ===================================================================
    private void stubAuthClientForUser(UserDto user) {
        when(authClient.getUserByEmail(anyString())).thenReturn(user);
    }

    // ===================================================================
    //  Helper: makes all quota count queries return 0 (no usage this month)
    // ===================================================================
    private void stubZeroQuotaUsage() {
        when(aiRequestRepository.countByUserIdAndTypeAndDate(anyString(), anyString(), any()))
                .thenReturn(0);
    }

    // ===================================================================
    //  1. generateSummary() Tests
    // ===================================================================
    @Nested
    @DisplayName("generateSummary()")
    class GenerateSummaryTests {

        @Test
        @DisplayName("Should call Gemini and return AI-generated summary for FREE user within quota")
        void generateSummary_success() {
            // ARRANGE: Set up a free user with 0 usage (within quota)
            stubAuthClientForUser(freeUser);
            stubZeroQuotaUsage();
            stubSaveRequest();

            // Gemini returns this AI-generated text
            when(geminiClient.callGemini(anyString(), anyString()))
                    .thenReturn("Experienced Java developer with 5 years of expertise.");

            // ACT: Call the method
            String result = aiService.generateSummary(
                    1L, "free@test.com", "Java Developer", "5 years", null);

            // ASSERT: The result should be the Gemini response
            assertEquals("Experienced Java developer with 5 years of expertise.", result);

            // VERIFY: Gemini was actually called once
            verify(geminiClient).callGemini(anyString(), eq("gemini-2.5-flash"));

            // VERIFY: The request was saved to the database (logged)
            verify(aiRequestRepository, times(2)).save(any(AiRequest.class));
            // Why 2 saves? → 1st save in logRequest() (status=QUEUED), 2nd in completeRequest() (status=COMPLETED)
        }

        @Test
        @DisplayName("Should return fallback text when Gemini API fails")
        void generateSummary_geminiFails_returnsFallback() {
            // ARRANGE: Gemini throws an exception (e.g., API quota exceeded)
            stubAuthClientForUser(freeUser);
            stubZeroQuotaUsage();
            stubSaveRequest();

            when(geminiClient.callGemini(anyString(), anyString()))
                    .thenThrow(new RuntimeException("API quota exceeded"));

            // ACT
            String result = aiService.generateSummary(
                    1L, "free@test.com", "Java Developer", "5 years", null);

            // ASSERT: Should contain fallback text (not throw exception)
            assertTrue(result.contains("Fallback Mode"));
            assertTrue(result.contains("Java Developer"));
        }

        @Test
        @DisplayName("Should throw exception when FREE user exceeds monthly quota")
        void generateSummary_quotaExceeded_throwsException() {
            // ARRANGE: Free user who already used 5 AI calls this month
            stubAuthClientForUser(freeUser);

            // Return 1 for each type → total = 5 → equals FREE_AI_MONTHLY_QUOTA
            when(aiRequestRepository.countByUserIdAndTypeAndDate(anyString(), anyString(), any()))
                    .thenReturn(1);

            // ACT & ASSERT: Should throw RuntimeException about quota
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> aiService.generateSummary(1L, "free@test.com", "Dev", "2y", null));

            assertTrue(ex.getMessage().contains("quota"));

            // VERIFY: Gemini should NOT have been called (blocked before reaching AI)
            verify(geminiClient, never()).callGemini(anyString(), anyString());
        }

        @Test
        @DisplayName("Should use different prompt when currentSummary is provided")
        void generateSummary_withExistingSummary() {
            // ARRANGE: Premium user so no quota check needed
            stubAuthClientForUser(premiumUser);
            stubSaveRequest();

            when(geminiClient.callGemini(anyString(), anyString()))
                    .thenReturn("Improved professional summary.");

            // ACT: Pass an existing summary to improve
            String result = aiService.generateSummary(
                    1L, "premium@test.com", "Dev", "3y", "I am a developer");

            // ASSERT
            assertEquals("Improved professional summary.", result);
            verify(geminiClient).callGemini(anyString(), anyString());
        }
    }

    // ===================================================================
    //  2. generateBulletPoints() Tests
    // ===================================================================
    @Nested
    @DisplayName("generateBulletPoints()")
    class GenerateBulletPointsTests {

        @Test
        @DisplayName("Should parse JSON response and return list of bullet points")
        void generateBulletPoints_success() {
            // ARRANGE
            stubAuthClientForUser(freeUser);
            stubZeroQuotaUsage();
            stubSaveRequest();

            // Gemini returns a JSON string with bullet points
            String jsonResponse = "{\"bullets\":[\"Led team of 5\",\"Improved performance by 30%\",\"Deployed microservices\"]}";
            when(geminiClient.callGeminiJsonMode(anyString(), anyString()))
                    .thenReturn(jsonResponse);

            // ACT
            List<String> bullets = aiService.generateBulletPoints(
                    1L, "free@test.com", "Backend Dev", "Built REST APIs");

            // ASSERT: Should return 3 parsed bullet points
            assertEquals(3, bullets.size());
            assertEquals("Led team of 5", bullets.get(0));
        }

        @Test
        @DisplayName("Should return fallback bullets when Gemini fails")
        void generateBulletPoints_geminiFails() {
            // ARRANGE
            stubAuthClientForUser(freeUser);
            stubZeroQuotaUsage();
            stubSaveRequest();

            when(geminiClient.callGeminiJsonMode(anyString(), anyString()))
                    .thenThrow(new RuntimeException("Gemini down"));

            // ACT
            List<String> bullets = aiService.generateBulletPoints(
                    1L, "free@test.com", "Dev", "Built APIs");

            // ASSERT: Fallback returns 3 hardcoded bullets
            assertEquals(3, bullets.size());
            assertTrue(bullets.get(2).contains("Fallback"));
        }
    }

    // ===================================================================
    //  3. generateCoverLetter() Tests (PREMIUM-ONLY feature)
    // ===================================================================
    @Nested
    @DisplayName("generateCoverLetter()")
    class GenerateCoverLetterTests {

        @Test
        @DisplayName("Should generate cover letter for PREMIUM user")
        void generateCoverLetter_premiumUser_success() {
            // ARRANGE
            stubAuthClientForUser(premiumUser);
            stubSaveRequest();

            when(geminiClient.callGemini(anyString(), anyString()))
                    .thenReturn("Dear Hiring Manager, I am excited to apply...");

            // ACT
            Map<String, Object> jobDetails = new HashMap<>();
            jobDetails.put("title", "Senior Java Dev");
            jobDetails.put("company", "Google");

            String result = aiService.generateCoverLetter(1L, "premium@test.com", jobDetails);

            // ASSERT
            assertTrue(result.contains("Dear Hiring Manager"));
            verify(geminiClient).callGemini(anyString(), anyString());
        }

        @Test
        @DisplayName("Should BLOCK free user from accessing cover letter feature")
        void generateCoverLetter_freeUser_blocked() {
            // ARRANGE: Free user tries to access premium-only feature
            stubAuthClientForUser(freeUser);

            // ACT & ASSERT
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> aiService.generateCoverLetter(1L, "free@test.com", new HashMap<>()));

            // The exception message should indicate this is a premium feature
            assertTrue(ex.getMessage().contains("PREMIUM_REQUIRED"));

            // VERIFY: Gemini should NEVER be called for free users
            verify(geminiClient, never()).callGemini(anyString(), anyString());
        }
    }

    // ===================================================================
    //  4. improveSection() Tests (PREMIUM-ONLY feature)
    // ===================================================================
    @Nested
    @DisplayName("improveSection()")
    class ImproveSectionTests {

        @Test
        @DisplayName("Should improve section text for PREMIUM user")
        void improveSection_premiumUser_success() {
            // ARRANGE
            stubAuthClientForUser(premiumUser);
            stubSaveRequest();

            when(geminiClient.callGemini(anyString(), anyString()))
                    .thenReturn("Enhanced and polished section content.");

            // ACT
            String result = aiService.improveSection(1L, "premium@test.com", "I did some work here");

            // ASSERT
            assertEquals("Enhanced and polished section content.", result);
        }

        @Test
        @DisplayName("Should block FREE user from improving sections")
        void improveSection_freeUser_blocked() {
            // ARRANGE
            stubAuthClientForUser(freeUser);

            // ACT & ASSERT: Free user cannot use this premium feature
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> aiService.improveSection(1L, "free@test.com", "some text"));

            assertTrue(ex.getMessage().contains("PREMIUM_REQUIRED"));
        }
    }

    // ===================================================================
    //  5. suggestSkills() Tests
    // ===================================================================
    @Nested
    @DisplayName("suggestSkills()")
    class SuggestSkillsTests {

        @Test
        @DisplayName("Should return parsed list of skills from Gemini")
        void suggestSkills_success() {
            // ARRANGE
            stubAuthClientForUser(freeUser);
            stubZeroQuotaUsage();
            stubSaveRequest();

            // Gemini returns JSON with skills array
            String jsonResponse = "{\"skills\":[\"Java\",\"Spring Boot\",\"Docker\",\"Kubernetes\",\"AWS\"]}";
            when(geminiClient.callGeminiJsonMode(anyString(), anyString()))
                    .thenReturn(jsonResponse);

            // ACT
            List<String> skills = aiService.suggestSkills(1L, "free@test.com", "Backend Engineer");

            // ASSERT: Should parse 5 skills from the JSON
            assertEquals(5, skills.size());
            assertTrue(skills.contains("Java"));
            assertTrue(skills.contains("Docker"));
        }

        @Test
        @DisplayName("Should return fallback skills when Gemini fails")
        void suggestSkills_geminiFails() {
            // ARRANGE
            stubAuthClientForUser(freeUser);
            stubZeroQuotaUsage();
            stubSaveRequest();

            when(geminiClient.callGeminiJsonMode(anyString(), anyString()))
                    .thenThrow(new RuntimeException("API error"));

            // ACT
            List<String> skills = aiService.suggestSkills(1L, "free@test.com", "Dev");

            // ASSERT: Should return fallback skills (hardcoded)
            assertFalse(skills.isEmpty());
            assertTrue(skills.get(0).contains("Communication"));
        }
    }

    // ===================================================================
    //  6. getAiHistory() Tests
    // ===================================================================
    @Nested
    @DisplayName("getAiHistory()")
    class GetAiHistoryTests {

        @Test
        @DisplayName("Should return the list of AI requests for a user from the database")
        void getAiHistory_returnsRequestList() {
            // ARRANGE: Create 2 fake AI request records in the database
            AiRequest req1 = AiRequest.builder().userId("user1").requestType("SUMMARY").build();
            AiRequest req2 = AiRequest.builder().userId("user1").requestType("BULLETS").build();

            when(aiRequestRepository.findByUserId("user1"))
                    .thenReturn(Arrays.asList(req1, req2));

            // ACT
            List<AiRequest> history = aiService.getAiHistory("user1");

            // ASSERT: Should return both records
            assertEquals(2, history.size());
            assertEquals("SUMMARY", history.get(0).getRequestType());
            assertEquals("BULLETS", history.get(1).getRequestType());

            // VERIFY: Repository was queried exactly once
            verify(aiRequestRepository).findByUserId("user1");
        }

        @Test
        @DisplayName("Should return empty list when user has no AI history")
        void getAiHistory_emptyList() {
            // ARRANGE
            when(aiRequestRepository.findByUserId("newuser")).thenReturn(List.of());

            // ACT
            List<AiRequest> history = aiService.getAiHistory("newuser");

            // ASSERT: Empty list, not null
            assertNotNull(history);
            assertTrue(history.isEmpty());
        }
    }

    // ===================================================================
    //  7. getRemainingQuota() Tests
    // ===================================================================
    @Nested
    @DisplayName("getRemainingQuota()")
    class GetRemainingQuotaTests {

        @Test
        @DisplayName("PREMIUM user should always have 999 remaining quota (unlimited)")
        void getRemainingQuota_premiumUser() {
            // ARRANGE: Premium user
            stubAuthClientForUser(premiumUser);

            // ACT
            int remaining = aiService.getRemainingQuota("premium@test.com");

            // ASSERT: Premium users get 999 (effectively unlimited)
            assertEquals(999, remaining);

            // VERIFY: Should NOT query the database for usage counts
            verify(aiRequestRepository, never())
                    .countByUserIdAndTypeAndDate(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("FREE user with 0 usage should have 5 remaining")
        void getRemainingQuota_freeUser_noUsage() {
            // ARRANGE: Free user, no usage this month
            stubAuthClientForUser(freeUser);
            stubZeroQuotaUsage();

            // ACT
            int remaining = aiService.getRemainingQuota("free@test.com");

            // ASSERT: 5 (limit) - 0 (used) = 5
            assertEquals(5, remaining);
        }

        @Test
        @DisplayName("FREE user with 3 total usages should have 2 remaining")
        void getRemainingQuota_freeUser_partialUsage() {
            // ARRANGE: Free user with some usage
            stubAuthClientForUser(freeUser);

            // Simulate: SUMMARY=1, BULLETS=1, IMPROVE=1, SKILLS=0, ATS=0 → total = 3
            when(aiRequestRepository.countByUserIdAndTypeAndDate(anyString(), eq("SUMMARY"), any())).thenReturn(1);
            when(aiRequestRepository.countByUserIdAndTypeAndDate(anyString(), eq("BULLETS"), any())).thenReturn(1);
            when(aiRequestRepository.countByUserIdAndTypeAndDate(anyString(), eq("IMPROVE"), any())).thenReturn(1);
            when(aiRequestRepository.countByUserIdAndTypeAndDate(anyString(), eq("SKILLS"), any())).thenReturn(0);
            when(aiRequestRepository.countByUserIdAndTypeAndDate(anyString(), eq("ATS"), any())).thenReturn(0);

            // ACT
            int remaining = aiService.getRemainingQuota("free@test.com");

            // ASSERT: 5 - 3 = 2
            assertEquals(2, remaining);
        }

        @Test
        @DisplayName("FREE user with 5+ usages should have 0 remaining (not negative)")
        void getRemainingQuota_freeUser_exhausted() {
            // ARRANGE: Free user who used all 5 calls
            stubAuthClientForUser(freeUser);

            // Each type returns 1 → total = 5 types × 1 = 5
            when(aiRequestRepository.countByUserIdAndTypeAndDate(anyString(), anyString(), any()))
                    .thenReturn(1);

            // ACT
            int remaining = aiService.getRemainingQuota("free@test.com");

            // ASSERT: Max(0, 5-5) = 0, never goes negative
            assertEquals(0, remaining);
        }
    }
}
