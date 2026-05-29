package com.resumeai.resume.service;

import com.resumeai.resume.client.AuthClient;
import com.resumeai.resume.client.UserDto;
import com.resumeai.resume.repository.ResumeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ResumeQuotaService.
 *
 * This service is responsible for checking if a user has reached their resume creation limit.
 * It queries the auth-service (via AuthClient) to check the user's plan.
 * 
 * Rules Tested:
 *  - PREMIUM and ADMIN users have unlimited resumes.
 *  - FREE users are limited to a max of 3 resumes.
 *  - Fail-open mechanism: If auth-service is down, we allow creation to not block the user.
 */
@ExtendWith(MockitoExtension.class)
class ResumeQuotaServiceTest {

    @Mock
    private AuthClient authClient;

    @Mock
    private ResumeRepository resumeRepository;

    @InjectMocks
    private ResumeQuotaService resumeQuotaService;

    private UserDto freeUser;
    private UserDto premiumUser;
    private UserDto adminUser;

    @BeforeEach
    void setUp() {
        freeUser = new UserDto();
        freeUser.setEmail("free@test.com");
        freeUser.setSubscriptionPlan("FREE");
        freeUser.setRole("USER");

        premiumUser = new UserDto();
        premiumUser.setEmail("premium@test.com");
        premiumUser.setSubscriptionPlan("PREMIUM");
        premiumUser.setRole("USER");

        adminUser = new UserDto();
        adminUser.setEmail("admin@test.com");
        adminUser.setSubscriptionPlan("FREE"); // plan doesn't matter for admin
        adminUser.setRole("ADMIN");
    }

    @Nested
    @DisplayName("checkResumeQuota()")
    class CheckResumeQuotaTests {

        @Test
        @DisplayName("Should bypass quota check for PREMIUM users")
        void checkResumeQuota_premiumUser() {
            // ARRANGE
            when(authClient.getUserByEmail("premium@test.com")).thenReturn(premiumUser);

            // ACT & ASSERT
            assertDoesNotThrow(() -> resumeQuotaService.checkResumeQuota("premium@test.com"));

            // VERIFY: ResumeRepository should NOT be queried to check counts
            verify(resumeRepository, never()).countByUserEmail(anyString());
        }

        @Test
        @DisplayName("Should bypass quota check for ADMIN users")
        void checkResumeQuota_adminUser() {
            // ARRANGE
            when(authClient.getUserByEmail("admin@test.com")).thenReturn(adminUser);

            // ACT & ASSERT
            assertDoesNotThrow(() -> resumeQuotaService.checkResumeQuota("admin@test.com"));

            // VERIFY
            verify(resumeRepository, never()).countByUserEmail(anyString());
        }

        @Test
        @DisplayName("Should pass FREE user if they have fewer than 3 resumes")
        void checkResumeQuota_freeUser_underLimit() {
            // ARRANGE
            when(authClient.getUserByEmail("free@test.com")).thenReturn(freeUser);
            // Simulate user having only 2 resumes
            when(resumeRepository.countByUserEmail("free@test.com")).thenReturn(2L);

            // ACT & ASSERT
            assertDoesNotThrow(() -> resumeQuotaService.checkResumeQuota("free@test.com"));
        }

        @Test
        @DisplayName("Should throw Exception if FREE user has 3 or more resumes")
        void checkResumeQuota_freeUser_limitReached() {
            // ARRANGE
            when(authClient.getUserByEmail("free@test.com")).thenReturn(freeUser);
            // Simulate user having reached the limit of 3
            when(resumeRepository.countByUserEmail("free@test.com")).thenReturn(3L);

            // ACT & ASSERT
            RuntimeException ex = assertThrows(RuntimeException.class, 
                () -> resumeQuotaService.checkResumeQuota("free@test.com"));
            
            assertTrue(ex.getMessage().contains("Resume limit reached"));
        }

        @Test
        @DisplayName("Should allow resume creation if auth-service throws exception (fail-open)")
        void checkResumeQuota_authServiceFails_allowsCreation() {
            // ARRANGE: Simulate auth-service being down
            when(authClient.getUserByEmail("user@test.com")).thenThrow(new RuntimeException("Service down"));

            // ACT & ASSERT: Should catch the exception and allow passage without throwing
            assertDoesNotThrow(() -> resumeQuotaService.checkResumeQuota("user@test.com"));
            
            // VERIFY: Should not proceed to checking the database since it bypassed the rest of the method
            verify(resumeRepository, never()).countByUserEmail(anyString());
        }
    }
}
