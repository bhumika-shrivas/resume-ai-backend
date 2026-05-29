package com.resumeai.auth.service;

import com.resumeai.auth.entity.User;
import com.resumeai.auth.entity.UserUsage;
import com.resumeai.auth.repository.UserRepository;
import com.resumeai.auth.repository.UserUsageRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SubscriptionService.
 *
 * SubscriptionService manages user subscription plans and usage tracking:
 *   - upgradeToPremium()   → sets subscriptionPlan to "PREMIUM" and saves
 *   - downgradeToFree()    → sets subscriptionPlan to "FREE" and saves
 *   - getUsage()           → returns existing UserUsage or creates a new one
 *   - incrementAiCall()    → increments the AI call counter by 1
 *   - incrementAtsCheck()  → increments the ATS check counter by 1
 *
 * KEY DESIGN NOTE:
 *   - incrementAiCall/AtsCheck internally call getUsage() first,
 *     which may create a new UserUsage if none exists.
 *   - This means save() can be called TWICE: once in getUsage() (new creation)
 *     and once in the increment method itself.
 *
 * We mock UserRepository and UserUsageRepository to avoid database access.
 */
@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    private UserRepository userRepository;  // Database access for User entity

    @Mock
    private UserUsageRepository usageRepository;  // Database access for UserUsage entity

    @InjectMocks
    private SubscriptionService subscriptionService;  // The class under test

    // ──────────────────────────────────────────────
    //  upgradeToPremium() Tests
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("upgradeToPremium()")
    class UpgradeToPremiumTests {

        @Test
        @DisplayName("Should set subscription plan to PREMIUM and save the user")
        void upgradeToPremium_success() {
            // ARRANGE: Create a FREE user
            Long userId = 1L;
            User user = new User();
            user.setId(userId);
            user.setSubscriptionPlan("FREE");

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            // ACT: Upgrade the user
            User result = subscriptionService.upgradeToPremium(userId);

            // ASSERT: Plan should now be "PREMIUM"
            assertEquals("PREMIUM", result.getSubscriptionPlan());
            verify(userRepository).findById(userId);

            // Use ArgumentCaptor to inspect the saved user
            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertEquals("PREMIUM", captor.getValue().getSubscriptionPlan());
        }

        @Test
        @DisplayName("Should throw RuntimeException when user is not found")
        void upgradeToPremium_userNotFound() {
            // ARRANGE: User doesn't exist
            Long userId = 99L;
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            // ACT & ASSERT
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> subscriptionService.upgradeToPremium(userId));

            assertEquals("User not found", ex.getMessage());
            verify(userRepository).findById(userId);
            // VERIFY: save() should never be called if user doesn't exist
            verify(userRepository, never()).save(any(User.class));
        }
    }

    // ──────────────────────────────────────────────
    //  downgradeToFree() Tests
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("downgradeToFree()")
    class DowngradeToFreeTests {

        @Test
        @DisplayName("Should set subscription plan to FREE and save the user")
        void downgradeToFree_success() {
            // ARRANGE: Create a PREMIUM user to downgrade
            Long userId = 2L;
            User user = new User();
            user.setId(userId);
            user.setSubscriptionPlan("PREMIUM");

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            // ACT
            User result = subscriptionService.downgradeToFree(userId);

            // ASSERT: Plan should now be "FREE"
            assertEquals("FREE", result.getSubscriptionPlan());
            verify(userRepository).findById(userId);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertEquals("FREE", captor.getValue().getSubscriptionPlan());
        }

        @Test
        @DisplayName("Should throw RuntimeException when user is not found")
        void downgradeToFree_userNotFound() {
            Long userId = 99L;
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> subscriptionService.downgradeToFree(userId));

            assertEquals("User not found", ex.getMessage());
            verify(userRepository).findById(userId);
            verify(userRepository, never()).save(any(User.class));
        }
    }

    // ──────────────────────────────────────────────
    //  getUsage() Tests
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("getUsage()")
    class GetUsageTests {

        @Test
        @DisplayName("Should return existing usage when found in repository")
        void getUsage_existingUsage() {
            // ARRANGE: Usage record already exists for this user
            Long userId = 5L;
            UserUsage existingUsage = new UserUsage(userId);
            existingUsage.setAiCallsThisMonth(3);
            existingUsage.setAtsChecksThisMonth(2);

            when(usageRepository.findById(userId)).thenReturn(Optional.of(existingUsage));

            // ACT
            UserUsage result = subscriptionService.getUsage(userId);

            // ASSERT: Returns the existing usage object (same reference)
            assertSame(existingUsage, result);
            assertEquals(3, result.getAiCallsThisMonth());
            assertEquals(2, result.getAtsChecksThisMonth());
            verify(usageRepository).findById(userId);
            // VERIFY: save() should NOT be called when usage already exists
            verify(usageRepository, never()).save(any(UserUsage.class));
        }

        @Test
        @DisplayName("Should create and save new usage when not found")
        void getUsage_createsNewUsageWhenNotFound() {
            // ARRANGE: No usage record exists for this user
            Long userId = 10L;

            when(usageRepository.findById(userId)).thenReturn(Optional.empty());
            when(usageRepository.save(any(UserUsage.class))).thenAnswer(inv -> inv.getArgument(0));

            // ACT
            UserUsage result = subscriptionService.getUsage(userId);

            // ASSERT: A new UserUsage was created with zero counters
            assertNotNull(result);
            assertEquals(userId, result.getUserId());
            assertEquals(0, result.getAiCallsThisMonth());
            assertEquals(0, result.getAtsChecksThisMonth());

            // VERIFY: The new usage was saved to the database
            ArgumentCaptor<UserUsage> captor = ArgumentCaptor.forClass(UserUsage.class);
            verify(usageRepository).save(captor.capture());
            assertEquals(userId, captor.getValue().getUserId());
        }
    }

    // ──────────────────────────────────────────────
    //  incrementAiCall() Tests
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("incrementAiCall()")
    class IncrementAiCallTests {

        @Test
        @DisplayName("Should increment aiCallsThisMonth by 1 and save")
        void incrementAiCall_incrementsAndSaves() {
            // ARRANGE: User already has 4 AI calls this month
            Long userId = 7L;
            UserUsage usage = new UserUsage(userId);
            usage.setAiCallsThisMonth(4);

            when(usageRepository.findById(userId)).thenReturn(Optional.of(usage));
            when(usageRepository.save(any(UserUsage.class))).thenAnswer(inv -> inv.getArgument(0));

            // ACT: Increment by 1
            subscriptionService.incrementAiCall(userId);

            // ASSERT: 4 + 1 = 5
            assertEquals(5, usage.getAiCallsThisMonth());

            ArgumentCaptor<UserUsage> captor = ArgumentCaptor.forClass(UserUsage.class);
            verify(usageRepository).save(captor.capture());
            assertEquals(5, captor.getValue().getAiCallsThisMonth());
        }

        @Test
        @DisplayName("Should increment from zero when usage is freshly created")
        void incrementAiCall_fromZero() {
            // ARRANGE: No existing usage → getUsage() will create one first
            Long userId = 20L;

            when(usageRepository.findById(userId)).thenReturn(Optional.empty());
            // save() is called twice: once by getUsage() to persist the new record,
            // and once by incrementAiCall() to persist the updated counter
            when(usageRepository.save(any(UserUsage.class))).thenAnswer(inv -> inv.getArgument(0));

            // ACT
            subscriptionService.incrementAiCall(userId);

            // ASSERT: The LAST saved value should have aiCallsThisMonth = 1
            ArgumentCaptor<UserUsage> captor = ArgumentCaptor.forClass(UserUsage.class);
            verify(usageRepository, times(2)).save(captor.capture());

            // Get the second (last) saved value
            UserUsage lastSaved = captor.getAllValues().get(1);
            assertEquals(1, lastSaved.getAiCallsThisMonth());
        }
    }

    // ──────────────────────────────────────────────
    //  incrementAtsCheck() Tests
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("incrementAtsCheck()")
    class IncrementAtsCheckTests {

        @Test
        @DisplayName("Should increment atsChecksThisMonth by 1 and save")
        void incrementAtsCheck_incrementsAndSaves() {
            // ARRANGE: User already has 6 ATS checks
            Long userId = 8L;
            UserUsage usage = new UserUsage(userId);
            usage.setAtsChecksThisMonth(6);

            when(usageRepository.findById(userId)).thenReturn(Optional.of(usage));
            when(usageRepository.save(any(UserUsage.class))).thenAnswer(inv -> inv.getArgument(0));

            // ACT: Increment by 1
            subscriptionService.incrementAtsCheck(userId);

            // ASSERT: 6 + 1 = 7
            assertEquals(7, usage.getAtsChecksThisMonth());

            ArgumentCaptor<UserUsage> captor = ArgumentCaptor.forClass(UserUsage.class);
            verify(usageRepository).save(captor.capture());
            assertEquals(7, captor.getValue().getAtsChecksThisMonth());
        }

        @Test
        @DisplayName("Should increment from zero when usage is freshly created")
        void incrementAtsCheck_fromZero() {
            // ARRANGE: No existing usage
            Long userId = 25L;

            when(usageRepository.findById(userId)).thenReturn(Optional.empty());
            when(usageRepository.save(any(UserUsage.class))).thenAnswer(inv -> inv.getArgument(0));

            // ACT
            subscriptionService.incrementAtsCheck(userId);

            // ASSERT: Two saves — one from getUsage() creation, one from increment
            ArgumentCaptor<UserUsage> captor = ArgumentCaptor.forClass(UserUsage.class);
            verify(usageRepository, times(2)).save(captor.capture());

            UserUsage lastSaved = captor.getAllValues().get(1);
            assertEquals(1, lastSaved.getAtsChecksThisMonth());
        }
    }
}
