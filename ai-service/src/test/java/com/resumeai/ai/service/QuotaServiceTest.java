package com.resumeai.ai.service;

import com.resumeai.ai.client.AuthClient;
import com.resumeai.ai.client.UserDto;
import com.resumeai.ai.client.UserUsageDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for QuotaService.
 *
 * QuotaService manages AI usage quotas:
 *   - checkAiQuota()     → allows PREMIUM/ADMIN users, blocks FREE users who exceed 5 calls/month
 *   - checkAtsQuota()    → same logic for ATS checks
 *   - incrementAiUsage() → increments the AI call counter for a user
 *   - incrementAtsUsage()→ increments the ATS check counter for a user
 *
 * We mock AuthClient (Feign client to auth-service) to avoid real HTTP calls.
 */
@ExtendWith(MockitoExtension.class)
class QuotaServiceTest {

    // ── MOCKS ─────────────────────────────────────────────────────────────
    @Mock
    private AuthClient authClient;  // Feign client that calls auth-service for user data

    // The class under test — AuthClient is injected automatically by Mockito
    @InjectMocks
    private QuotaService quotaService;

    // ── SHARED TEST DATA ─────────────────────────────────────────────────
    private UserDto premiumUser;
    private UserDto adminUser;
    private UserDto freeUser;
    private UserUsageDto lowUsage;
    private UserUsageDto maxUsage;

    @BeforeEach
    void setUp() {
        // PREMIUM user → unlimited access
        premiumUser = new UserDto();
        premiumUser.setEmail("premium@test.com");
        premiumUser.setSubscriptionPlan("PREMIUM");
        premiumUser.setRole("USER");

        // ADMIN user → also unlimited access
        adminUser = new UserDto();
        adminUser.setEmail("admin@test.com");
        adminUser.setSubscriptionPlan("FREE");
        adminUser.setRole("ADMIN");

        // FREE user → limited to 5 calls/month
        freeUser = new UserDto();
        freeUser.setEmail("free@test.com");
        freeUser.setSubscriptionPlan("FREE");
        freeUser.setRole("USER");

        // Usage object with low usage (within quota)
        lowUsage = new UserUsageDto();
        lowUsage.setAiCallsThisMonth(1);
        lowUsage.setAtsChecksThisMonth(1);
        // Total = 2, which is below the limit of 5

        // Usage object at max quota (at or above limit)
        maxUsage = new UserUsageDto();
        maxUsage.setAiCallsThisMonth(3);
        maxUsage.setAtsChecksThisMonth(2);
        // Total = 5, which equals the FREE_AI_LIMIT
    }

    // ===================================================================
    //  1. checkAiQuota() Tests
    // ===================================================================
    @Nested
    @DisplayName("checkAiQuota()")
    class CheckAiQuotaTests {

        @Test
        @DisplayName("PREMIUM user should pass quota check without checking usage")
        void checkAiQuota_premiumUser_allowed() {
            // ARRANGE: Auth client returns a premium user
            when(authClient.getUserByEmail("premium@test.com")).thenReturn(premiumUser);

            // ACT & ASSERT: Should NOT throw any exception
            assertDoesNotThrow(() -> quotaService.checkAiQuota("premium@test.com"));

            // VERIFY: Usage was never checked (premium users bypass usage check)
            verify(authClient, never()).getUsage(anyString());
        }

        @Test
        @DisplayName("ADMIN user should pass quota check without checking usage")
        void checkAiQuota_adminUser_allowed() {
            // ARRANGE: Auth client returns an admin user
            when(authClient.getUserByEmail("admin@test.com")).thenReturn(adminUser);

            // ACT & ASSERT: Admins also bypass quota
            assertDoesNotThrow(() -> quotaService.checkAiQuota("admin@test.com"));

            // VERIFY: No usage check for admins
            verify(authClient, never()).getUsage(anyString());
        }

        @Test
        @DisplayName("FREE user within quota should pass check")
        void checkAiQuota_freeUser_withinQuota_allowed() {
            // ARRANGE: Free user with low usage (2 total < 5 limit)
            when(authClient.getUserByEmail("free@test.com")).thenReturn(freeUser);
            when(authClient.getUsage("free@test.com")).thenReturn(lowUsage);

            // ACT & ASSERT: Should pass since 2 < 5
            assertDoesNotThrow(() -> quotaService.checkAiQuota("free@test.com"));
        }

        @Test
        @DisplayName("FREE user who exceeded quota should be BLOCKED with RuntimeException")
        void checkAiQuota_freeUser_quotaExceeded_blocked() {
            // ARRANGE: Free user with max usage (5 total >= 5 limit)
            when(authClient.getUserByEmail("free@test.com")).thenReturn(freeUser);
            when(authClient.getUsage("free@test.com")).thenReturn(maxUsage);

            // ACT & ASSERT: Should throw RuntimeException
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> quotaService.checkAiQuota("free@test.com"));

            // Exception message should mention quota and upgrade
            assertTrue(ex.getMessage().contains("quota"));
            assertTrue(ex.getMessage().contains("PREMIUM"));
        }
    }

    // ===================================================================
    //  2. checkAtsQuota() Tests (same logic as checkAiQuota)
    // ===================================================================
    @Nested
    @DisplayName("checkAtsQuota()")
    class CheckAtsQuotaTests {

        @Test
        @DisplayName("PREMIUM user bypasses ATS quota check")
        void checkAtsQuota_premiumUser_allowed() {
            when(authClient.getUserByEmail("premium@test.com")).thenReturn(premiumUser);

            assertDoesNotThrow(() -> quotaService.checkAtsQuota("premium@test.com"));
            verify(authClient, never()).getUsage(anyString());
        }

        @Test
        @DisplayName("FREE user within quota passes ATS check")
        void checkAtsQuota_freeUser_withinQuota() {
            when(authClient.getUserByEmail("free@test.com")).thenReturn(freeUser);
            when(authClient.getUsage("free@test.com")).thenReturn(lowUsage);

            assertDoesNotThrow(() -> quotaService.checkAtsQuota("free@test.com"));
        }

        @Test
        @DisplayName("FREE user exceeding quota is blocked from ATS")
        void checkAtsQuota_freeUser_quotaExceeded() {
            when(authClient.getUserByEmail("free@test.com")).thenReturn(freeUser);
            when(authClient.getUsage("free@test.com")).thenReturn(maxUsage);

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> quotaService.checkAtsQuota("free@test.com"));

            assertTrue(ex.getMessage().contains("quota"));
        }
    }

    // ===================================================================
    //  3. incrementAiUsage() Tests
    // ===================================================================
    @Nested
    @DisplayName("incrementAiUsage()")
    class IncrementAiUsageTests {

        @Test
        @DisplayName("Should call authClient to increment AI usage counter")
        void incrementAiUsage_callsAuthClient() {
            // ARRANGE: Return any user (method fetches user first, then increments)
            when(authClient.getUserByEmail("user@test.com")).thenReturn(freeUser);

            // ACT
            quotaService.incrementAiUsage("user@test.com");

            // VERIFY: Both getUserByEmail and incrementAi were called
            verify(authClient).getUserByEmail("user@test.com");
            verify(authClient).incrementAi("user@test.com");
        }
    }

    // ===================================================================
    //  4. incrementAtsUsage() Tests
    // ===================================================================
    @Nested
    @DisplayName("incrementAtsUsage()")
    class IncrementAtsUsageTests {

        @Test
        @DisplayName("Should call authClient to increment ATS usage counter")
        void incrementAtsUsage_callsAuthClient() {
            // ARRANGE
            when(authClient.getUserByEmail("user@test.com")).thenReturn(freeUser);

            // ACT
            quotaService.incrementAtsUsage("user@test.com");

            // VERIFY: Both getUserByEmail and incrementAts were called
            verify(authClient).getUserByEmail("user@test.com");
            verify(authClient).incrementAts("user@test.com");
        }
    }
}
