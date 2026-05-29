package com.resumeai.auth.service;

import com.resumeai.auth.dto.ChangePasswordRequest;
import com.resumeai.auth.dto.JwtResponse;
import com.resumeai.auth.dto.TokenRefreshRequest;
import com.resumeai.auth.dto.UpdateProfileRequest;
import com.resumeai.auth.entity.AuditLog;
import com.resumeai.auth.entity.User;
import com.resumeai.auth.repository.UserRepository;
import com.resumeai.auth.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthServiceImpl — the main authentication service.
 *
 * WHAT THIS CLASS DOES:
 *   - Registration, Login, Logout
 *   - JWT token validation & refresh
 *   - Profile updates, Password changes
 *   - Subscription management, Account deactivation
 *   - OAuth2 login (Google/LinkedIn)
 *   - Password reset via OTP
 *
 * HOW WE TEST IT:
 *   - We mock ALL dependencies (UserRepository, JwtUtil, RedisService, etc.)
 *   - PasswordEncoder is special: the real class does `new BCryptPasswordEncoder()` directly,
 *     so we use ReflectionTestUtils.setField() to replace it with a mock
 *   - Each test verifies both the RETURN VALUE and the INTERACTIONS with mocks
 *
 * TEST NAMING CONVENTION:
 *   methodName_scenario → e.g., register_Success, login_UserNotFound
 */
@ExtendWith(MockitoExtension.class)    // Enables Mockito annotations without loading Spring context
class AuthServiceImplTest {

    // ── MOCK DEPENDENCIES ────────────────────────────────────────────────
    // Each @Mock creates a fake version of the dependency.
    // Mockito automatically injects these into @InjectMocks.

    @Mock
    private UserRepository userRepository;  // Database access for User entity

    @Mock
    private JwtUtil jwtUtil;               // JWT token generation & validation

    @Mock
    private RedisService redisService;      // Redis cache (stores refresh tokens, OTPs, blacklists)

    @Mock
    private AuditService auditService;      // Audit logging (tracks user actions)

    @Mock
    private EmailService emailService;      // Email sending (OTP emails)

    @Mock
    private PasswordEncoder passwordEncoder; // Password hashing (BCrypt)

    // The class under test — all @Mock fields are automatically injected into this
    @InjectMocks
    private AuthServiceImpl authService;

    // ── SHARED TEST DATA ─────────────────────────────────────────────────
    private User testUser;   // A reusable test user object

    @BeforeEach
    void setUp() {
        // IMPORTANT: AuthServiceImpl creates passwordEncoder as `new BCryptPasswordEncoder()`,
        // NOT via @Autowired. So Mockito's @InjectMocks can't inject our mock.
        // We use Spring's ReflectionTestUtils to forcefully replace it with our mock.
        ReflectionTestUtils.setField(authService, "passwordEncoder", passwordEncoder);

        // Create a standard test user that many tests will reuse
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("test@example.com");
        testUser.setFullName("Test User");
        testUser.setPasswordHash("hashedPassword");  // This represents an already-encoded password
        testUser.setRole("USER");
        testUser.setProvider("LOCAL");
        testUser.setSubscriptionPlan("FREE");
        testUser.setActive(true);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  1. register() — User Registration
    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("register()")
    class RegisterTests {

        @Test
        @DisplayName("Should register a new user successfully")
        void register_Success() {
            // ARRANGE: Create a new user with a raw password
            User newUser = new User();
            newUser.setEmail("new@example.com");
            newUser.setPasswordHash("rawPassword");   // This is the raw password before encoding

            // Mock behavior:
            when(userRepository.existsByEmail("new@example.com")).thenReturn(false);  // Email not taken
            when(passwordEncoder.encode("rawPassword")).thenReturn("encodedPassword"); // BCrypt encoding
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User saved = invocation.getArgument(0);  // Return the same user with an ID set
                saved.setId(2L);
                return saved;
            });

            // ACT: Call the register method
            User result = authService.register(newUser);

            // ASSERT: Verify the returned user has correct defaults
            assertNotNull(result);
            assertEquals("new@example.com", result.getEmail());
            assertEquals("encodedPassword", result.getPasswordHash());  // Password was encoded
            assertEquals("USER", result.getRole());          // Default role
            assertEquals("LOCAL", result.getProvider());      // Default provider
            assertEquals("FREE", result.getSubscriptionPlan()); // Default plan
            assertTrue(result.isActive());                    // Account is active

            // VERIFY: Check that the right methods were called
            verify(userRepository).existsByEmail("new@example.com");
            verify(passwordEncoder).encode("rawPassword");
            verify(userRepository).save(any(User.class));
            verify(auditService).log(eq("new@example.com"), eq("REGISTER"), anyString()); // Audit logged
        }

        @Test
        @DisplayName("Should throw exception when email already exists")
        void register_EmailAlreadyExists() {
            // ARRANGE: Email already taken in database
            User newUser = new User();
            newUser.setEmail("existing@example.com");

            when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

            // ACT & ASSERT: Should throw RuntimeException
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> authService.register(newUser));
            assertEquals("Email already exists", ex.getMessage());

            // VERIFY: Database save should NEVER be called (early exit)
            verify(userRepository, never()).save(any(User.class));
            verify(auditService, never()).log(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Should register user with null password without encoding")
        void register_NullPassword() {
            // ARRANGE: OAuth users might have null password
            User newUser = new User();
            newUser.setEmail("nopass@example.com");
            newUser.setPasswordHash(null);   // No password for OAuth users

            when(userRepository.existsByEmail("nopass@example.com")).thenReturn(false);
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User saved = invocation.getArgument(0);
                saved.setId(3L);
                return saved;
            });

            // ACT
            User result = authService.register(newUser);

            // ASSERT: Password should remain null (not encoded)
            assertNotNull(result);
            assertNull(result.getPasswordHash());

            // VERIFY: encode() should NEVER be called when password is null
            verify(passwordEncoder, never()).encode(anyString());
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("Should register user with empty password without encoding")
        void register_EmptyPassword() {
            // ARRANGE: Empty string password should also skip encoding
            User newUser = new User();
            newUser.setEmail("emptypass@example.com");
            newUser.setPasswordHash("");

            when(userRepository.existsByEmail("emptypass@example.com")).thenReturn(false);
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User saved = invocation.getArgument(0);
                saved.setId(4L);
                return saved;
            });

            // ACT
            User result = authService.register(newUser);

            // ASSERT
            assertNotNull(result);
            assertEquals("", result.getPasswordHash());  // Stays empty
            verify(passwordEncoder, never()).encode(anyString());
            verify(userRepository).save(any(User.class));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  2. login() — User Login with email/password
    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("login()")
    class LoginTests {

        @Test
        @DisplayName("Should login successfully and return JwtResponse with tokens")
        void login_Success() {
            // ARRANGE: User exists, is active, and password matches
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches("rawPassword", "hashedPassword")).thenReturn(true);
            when(jwtUtil.generateToken("test@example.com", "USER", "FREE")).thenReturn("access-token");
            when(jwtUtil.generateRefreshToken("test@example.com")).thenReturn("refresh-token");

            // ACT
            JwtResponse response = authService.login("test@example.com", "rawPassword");

            // ASSERT: JwtResponse should contain all token info
            assertNotNull(response);
            assertEquals("access-token", response.getAccessToken());
            assertEquals("refresh-token", response.getRefreshToken());
            assertEquals("USER", response.getRole());
            assertEquals("FREE", response.getPlan());

            // VERIFY: Refresh token stored in Redis for 7 days
            verify(redisService).setValue("refresh-token", "test@example.com", 7, TimeUnit.DAYS);
            // VERIFY: Login action is audit-logged
            verify(auditService).log(eq("test@example.com"), eq("LOGIN"), anyString());
        }

        @Test
        @DisplayName("Should throw exception when user not found")
        void login_UserNotFound() {
            // ARRANGE: No user with this email in database
            when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

            // ACT & ASSERT
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> authService.login("unknown@example.com", "password"));
            assertEquals("User not found", ex.getMessage());

            // VERIFY: JWT generation should never happen
            verify(jwtUtil, never()).generateToken(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Should throw exception when account is deactivated")
        void login_AccountDeactivated() {
            // ARRANGE: User exists but account is deactivated
            testUser.setActive(false);
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

            // ACT & ASSERT
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> authService.login("test@example.com", "rawPassword"));
            assertEquals("Account is deactivated", ex.getMessage());

            // VERIFY: Password check is skipped entirely (early exit after active check)
            verify(passwordEncoder, never()).matches(anyString(), anyString());
        }

        @Test
        @DisplayName("Should throw exception when password does not match")
        void login_InvalidPassword() {
            // ARRANGE: User exists, active, but wrong password
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches("wrongPassword", "hashedPassword")).thenReturn(false);

            // ACT & ASSERT
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> authService.login("test@example.com", "wrongPassword"));
            assertEquals("Invalid password", ex.getMessage());

            // VERIFY: No token generated for wrong password
            verify(jwtUtil, never()).generateToken(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Should throw exception when password hash is null (OAuth user trying local login)")
        void login_NullPasswordHash() {
            // ARRANGE: User registered via OAuth (no password hash stored)
            testUser.setPasswordHash(null);
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

            // ACT & ASSERT: null password hash → invalid password
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> authService.login("test@example.com", "anyPassword"));
            assertEquals("Invalid password", ex.getMessage());

            // VERIFY: matches() is never called because of null check: `user.getPasswordHash() == null`
            verify(passwordEncoder, never()).matches(anyString(), anyString());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  3. logout() — Blacklist the JWT token
    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("logout()")
    class LogoutTests {

        @Test
        @DisplayName("Should strip 'Bearer ' prefix and blacklist the raw token")
        void logout_WithBearerPrefix() {
            // ACT: Pass token with "Bearer " prefix (as it comes from HTTP header)
            authService.logout("Bearer some-jwt-token");

            // VERIFY: The prefix is stripped, only the raw token is blacklisted
            verify(redisService).blacklistToken("some-jwt-token");
        }

        @Test
        @DisplayName("Should blacklist token as-is when no Bearer prefix")
        void logout_WithoutBearerPrefix() {
            // ACT: Token without prefix
            authService.logout("some-jwt-token");

            // VERIFY: Passed through unchanged
            verify(redisService).blacklistToken("some-jwt-token");
        }

        @Test
        @DisplayName("Should handle null token without error")
        void logout_NullToken() {
            // ACT: null token (edge case)
            authService.logout(null);

            // VERIFY: null is passed to blacklistToken (handled gracefully by RedisService)
            verify(redisService).blacklistToken(null);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  4. validateToken() — Delegates to JwtUtil
    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("validateToken()")
    class ValidateTokenTests {

        @Test
        @DisplayName("Should return true when JwtUtil validates the token")
        void validateToken_Valid() {
            when(jwtUtil.validateToken("valid-token")).thenReturn(true);

            assertTrue(authService.validateToken("valid-token"));
            verify(jwtUtil).validateToken("valid-token");
        }

        @Test
        @DisplayName("Should return false when JwtUtil rejects the token")
        void validateToken_Invalid() {
            when(jwtUtil.validateToken("invalid-token")).thenReturn(false);

            assertFalse(authService.validateToken("invalid-token"));
            verify(jwtUtil).validateToken("invalid-token");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  5. getUserById()
    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("getUserById()")
    class GetUserByIdTests {

        @Test
        @DisplayName("Should return user when found by ID")
        void getUserById_Found() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

            User result = authService.getUserById(1L);

            assertNotNull(result);
            assertEquals(1L, result.getId());
            assertEquals("test@example.com", result.getEmail());
            verify(userRepository).findById(1L);
        }

        @Test
        @DisplayName("Should throw exception when user not found by ID")
        void getUserById_NotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> authService.getUserById(99L));
            assertEquals("User not found", ex.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  6. getUserByEmail()
    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("getUserByEmail()")
    class GetUserByEmailTests {

        @Test
        @DisplayName("Should return user when found by email")
        void getUserByEmail_Found() {
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

            User result = authService.getUserByEmail("test@example.com");

            assertNotNull(result);
            assertEquals("test@example.com", result.getEmail());
            verify(userRepository).findByEmail("test@example.com");
        }

        @Test
        @DisplayName("Should throw exception when user not found by email")
        void getUserByEmail_NotFound() {
            when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> authService.getUserByEmail("missing@example.com"));
            assertEquals("User not found", ex.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  7. updateProfile() — Update user profile fields
    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("updateProfile()")
    class UpdateProfileTests {

        @Test
        @DisplayName("Should update ALL profile fields when all are non-null")
        void updateProfile_FullUpdate() {
            // ARRANGE: Request with all fields set
            UpdateProfileRequest request = new UpdateProfileRequest();
            request.setFullName("Updated Name");
            request.setPhone("1234567890");
            request.setHeadline("Senior Dev");
            request.setAbout("About me text");

            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            // ACT
            User result = authService.updateProfile(1L, request);

            // ASSERT: All fields should be updated
            assertEquals("Updated Name", result.getFullName());
            assertEquals("1234567890", result.getPhone());
            assertEquals("Senior Dev", result.getHeadline());
            assertEquals("About me text", result.getAbout());
            verify(userRepository).save(testUser);
        }

        @Test
        @DisplayName("Should update ONLY non-null fields, keeping others unchanged (partial update)")
        void updateProfile_PartialUpdate() {
            // ARRANGE: Set original values first
            testUser.setFullName("Original Name");
            testUser.setPhone("0000000000");
            testUser.setHeadline("Original Headline");
            testUser.setAbout("Original About");

            // Only update fullName and about, leave phone and headline as null
            UpdateProfileRequest request = new UpdateProfileRequest();
            request.setFullName("New Name");
            request.setPhone(null);          // null → should NOT overwrite
            request.setHeadline(null);       // null → should NOT overwrite
            request.setAbout("New About");

            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            // ACT
            User result = authService.updateProfile(1L, request);

            // ASSERT: Only fullName and about changed; phone and headline unchanged
            assertEquals("New Name", result.getFullName());
            assertEquals("0000000000", result.getPhone());          // Unchanged!
            assertEquals("Original Headline", result.getHeadline()); // Unchanged!
            assertEquals("New About", result.getAbout());
            verify(userRepository).save(testUser);
        }

        @Test
        @DisplayName("Should throw exception when user not found")
        void updateProfile_UserNotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> authService.updateProfile(99L, new UpdateProfileRequest()));
            assertEquals("User not found", ex.getMessage());

            // VERIFY: No save should happen if user doesn't exist
            verify(userRepository, never()).save(any(User.class));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  8. changePassword()
    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("changePassword()")
    class ChangePasswordTests {

        @Test
        @DisplayName("Should change password when old password matches")
        void changePassword_Success() {
            // ARRANGE
            ChangePasswordRequest request = new ChangePasswordRequest();
            request.setOldPassword("oldPass");
            request.setNewPassword("newPass");

            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches("oldPass", "hashedPassword")).thenReturn(true); // Old password correct
            when(passwordEncoder.encode("newPass")).thenReturn("newHashedPassword");

            // ACT
            authService.changePassword(1L, request);

            // ASSERT: Password was updated on the user object
            assertEquals("newHashedPassword", testUser.getPasswordHash());
            verify(userRepository).save(testUser);
        }

        @Test
        @DisplayName("Should throw exception when old password is wrong")
        void changePassword_WrongOldPassword() {
            ChangePasswordRequest request = new ChangePasswordRequest();
            request.setOldPassword("wrongOldPass");
            request.setNewPassword("newPass");

            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches("wrongOldPass", "hashedPassword")).thenReturn(false); // Wrong!

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> authService.changePassword(1L, request));
            assertEquals("Old password is incorrect", ex.getMessage());

            // VERIFY: Password should NOT be updated
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("Should throw exception when user not found")
        void changePassword_UserNotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> authService.changePassword(99L, new ChangePasswordRequest()));
            assertEquals("User not found", ex.getMessage());
        }

        @Test
        @DisplayName("Should throw exception when password hash is null (OAuth user)")
        void changePassword_NullPasswordHash() {
            // ARRANGE: OAuth user has no password hash
            testUser.setPasswordHash(null);

            ChangePasswordRequest request = new ChangePasswordRequest();
            request.setOldPassword("anyOldPass");
            request.setNewPassword("newPass");

            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

            // ACT & ASSERT: null hash → "Old password is incorrect"
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> authService.changePassword(1L, request));
            assertEquals("Old password is incorrect", ex.getMessage());

            // VERIFY: matches() never called due to null check
            verify(passwordEncoder, never()).matches(anyString(), anyString());
            verify(userRepository, never()).save(any(User.class));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  9. updateSubscription()
    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("updateSubscription()")
    class UpdateSubscriptionTests {

        @Test
        @DisplayName("Should update subscription plan and log audit event")
        void updateSubscription_Success() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

            // ACT
            authService.updateSubscription(1L, "PRO");

            // ASSERT: Plan was updated on user
            assertEquals("PRO", testUser.getSubscriptionPlan());
            verify(userRepository).save(testUser);
            // VERIFY: Audit trail records the upgrade
            verify(auditService).log(eq("test@example.com"), eq("UPGRADE"), contains("PRO"));
        }

        @Test
        @DisplayName("Should throw exception when user not found")
        void updateSubscription_UserNotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> authService.updateSubscription(99L, "PRO"));
            assertEquals("User not found", ex.getMessage());

            verify(userRepository, never()).save(any(User.class));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  10. deactivateAccount()
    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("deactivateAccount()")
    class DeactivateAccountTests {

        @Test
        @DisplayName("Should set user as inactive and save")
        void deactivateAccount_Success() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

            // ACT
            authService.deactivateAccount(1L);

            // ASSERT: Account is now inactive
            assertFalse(testUser.isActive());
            verify(userRepository).save(testUser);
        }

        @Test
        @DisplayName("Should throw exception when user not found")
        void deactivateAccount_UserNotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> authService.deactivateAccount(99L));
            assertEquals("User not found", ex.getMessage());

            verify(userRepository, never()).save(any(User.class));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  11. refreshToken() — Rotate access + refresh tokens
    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("refreshToken()")
    class RefreshTokenTests {

        @Test
        @DisplayName("Should rotate tokens: delete old refresh, create new access + refresh")
        void refreshToken_Success() {
            // ARRANGE
            TokenRefreshRequest request = new TokenRefreshRequest();
            request.setRefreshToken("old-refresh-token");

            // Redis returns the email associated with this refresh token
            when(redisService.getValue("old-refresh-token")).thenReturn("test@example.com");
            when(jwtUtil.validateToken("old-refresh-token")).thenReturn(true);
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
            when(jwtUtil.generateToken("test@example.com", "USER", "FREE")).thenReturn("new-access-token");
            when(jwtUtil.generateRefreshToken("test@example.com")).thenReturn("new-refresh-token");

            // ACT
            JwtResponse response = authService.refreshToken(request);

            // ASSERT: New tokens returned
            assertNotNull(response);
            assertEquals("new-access-token", response.getAccessToken());
            assertEquals("new-refresh-token", response.getRefreshToken());
            assertEquals("USER", response.getRole());
            assertEquals("FREE", response.getPlan());

            // VERIFY: Old token deleted, new one stored
            verify(redisService).deleteValue("old-refresh-token");
            verify(redisService).setValue("new-refresh-token", "test@example.com", 7, TimeUnit.DAYS);
        }

        @Test
        @DisplayName("Should throw when refresh token not found in Redis (expired)")
        void refreshToken_NullEmailFromRedis() {
            TokenRefreshRequest request = new TokenRefreshRequest();
            request.setRefreshToken("expired-refresh-token");

            // Redis returns null → token has expired or was never stored
            when(redisService.getValue("expired-refresh-token")).thenReturn(null);

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> authService.refreshToken(request));
            assertEquals("Refresh token is invalid or expired", ex.getMessage());

            // VERIFY: JWT validation never reached
            verify(jwtUtil, never()).validateToken(anyString());
        }

        @Test
        @DisplayName("Should throw and cleanup when JWT validation fails")
        void refreshToken_InvalidJwtToken() {
            TokenRefreshRequest request = new TokenRefreshRequest();
            request.setRefreshToken("bad-jwt-refresh");

            when(redisService.getValue("bad-jwt-refresh")).thenReturn("test@example.com");
            when(jwtUtil.validateToken("bad-jwt-refresh")).thenReturn(false); // JWT signature invalid

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> authService.refreshToken(request));
            assertEquals("Refresh token is invalid or expired", ex.getMessage());

            // VERIFY: Invalid token is cleaned up from Redis
            verify(redisService).deleteValue("bad-jwt-refresh");
            // VERIFY: No new tokens generated
            verify(jwtUtil, never()).generateToken(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Should throw when user not found during token refresh")
        void refreshToken_UserNotFound() {
            TokenRefreshRequest request = new TokenRefreshRequest();
            request.setRefreshToken("valid-refresh");

            when(redisService.getValue("valid-refresh")).thenReturn("gone@example.com");
            when(jwtUtil.validateToken("valid-refresh")).thenReturn(true);
            when(userRepository.findByEmail("gone@example.com")).thenReturn(Optional.empty());

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> authService.refreshToken(request));
            assertEquals("User not found", ex.getMessage());
        }

        @Test
        @DisplayName("Should throw when account is deactivated during token refresh")
        void refreshToken_AccountDeactivated() {
            testUser.setActive(false); // Deactivated account

            TokenRefreshRequest request = new TokenRefreshRequest();
            request.setRefreshToken("valid-refresh");

            when(redisService.getValue("valid-refresh")).thenReturn("test@example.com");
            when(jwtUtil.validateToken("valid-refresh")).thenReturn(true);
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> authService.refreshToken(request));
            assertEquals("Account is deactivated", ex.getMessage());

            // VERIFY: No new tokens generated for deactivated accounts
            verify(jwtUtil, never()).generateToken(anyString(), anyString(), anyString());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  12. oauth2Login() — Google/LinkedIn OAuth2 login
    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("oauth2Login()")
    class OAuth2LoginTests {

        @Test
        @DisplayName("Should auto-register new user and return JWT on first OAuth2 login")
        void oauth2Login_NewUserRegistration() {
            // ARRANGE: User doesn't exist yet → will be created
            when(userRepository.findByEmail("new-oauth@example.com")).thenReturn(Optional.empty());
            when(passwordEncoder.encode(anyString())).thenReturn("random-encoded-pass");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(10L);  // Simulate auto-generated ID
                return u;
            });
            when(jwtUtil.generateToken("new-oauth@example.com", "USER", "FREE")).thenReturn("oauth-access");
            when(jwtUtil.generateRefreshToken("new-oauth@example.com")).thenReturn("oauth-refresh");

            // ACT
            JwtResponse response = authService.oauth2Login("new-oauth@example.com", "OAuth User", "GOOGLE");

            // ASSERT
            assertNotNull(response);
            assertEquals("oauth-access", response.getAccessToken());
            assertEquals("oauth-refresh", response.getRefreshToken());

            // VERIFY: New user was saved to database
            verify(userRepository).save(any(User.class));
            // VERIFY: Refresh token stored in Redis
            verify(redisService).setValue("oauth-refresh", "new-oauth@example.com", 7, TimeUnit.DAYS);
        }

        @Test
        @DisplayName("Should return JWT for existing active user without re-saving")
        void oauth2Login_ExistingActiveUser() {
            // ARRANGE: User already exists and is active
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
            when(jwtUtil.generateToken("test@example.com", "USER", "FREE")).thenReturn("access-tok");
            when(jwtUtil.generateRefreshToken("test@example.com")).thenReturn("refresh-tok");

            // ACT
            JwtResponse response = authService.oauth2Login("test@example.com", "Test User", "GOOGLE");

            // ASSERT
            assertNotNull(response);
            assertEquals("access-tok", response.getAccessToken());
            assertEquals("refresh-tok", response.getRefreshToken());

            // VERIFY: No save needed — user is already active
            verify(userRepository, never()).save(any(User.class));
            verify(redisService).setValue("refresh-tok", "test@example.com", 7, TimeUnit.DAYS);
        }

        @Test
        @DisplayName("Should reactivate inactive user on OAuth2 login")
        void oauth2Login_InactiveUserReactivation() {
            // ARRANGE: User exists but was deactivated
            testUser.setActive(false);
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
            when(jwtUtil.generateToken("test@example.com", "USER", "FREE")).thenReturn("reactivated-access");
            when(jwtUtil.generateRefreshToken("test@example.com")).thenReturn("reactivated-refresh");

            // ACT
            JwtResponse response = authService.oauth2Login("test@example.com", "Test User", "GOOGLE");

            // ASSERT: User was reactivated
            assertTrue(testUser.isActive());
            assertNotNull(response);
            assertEquals("reactivated-access", response.getAccessToken());

            // VERIFY: User saved because it was reactivated
            verify(userRepository).save(testUser);
        }

        @Test
        @DisplayName("Should default provider to 'GOOGLE' when null is passed")
        void oauth2Login_NullProvider() {
            // ARRANGE
            when(userRepository.findByEmail("null-provider@example.com")).thenReturn(Optional.empty());
            when(passwordEncoder.encode(anyString())).thenReturn("encoded");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(11L);
                return u;
            });
            when(jwtUtil.generateToken("null-provider@example.com", "USER", "FREE")).thenReturn("at");
            when(jwtUtil.generateRefreshToken("null-provider@example.com")).thenReturn("rt");

            // ACT
            authService.oauth2Login("null-provider@example.com", "NullProv User", null);

            // VERIFY: Provider defaults to "GOOGLE" when null
            verify(userRepository).save(argThat(user -> "GOOGLE".equals(user.getProvider())));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  13. getAuditLogs() — Delegates to AuditService
    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("getAuditLogs()")
    class GetAuditLogsTests {

        @Test
        @DisplayName("Should delegate to auditService and return all logs")
        void getAuditLogs_ReturnsLogs() {
            // ARRANGE: AuditService returns 2 log entries
            List<AuditLog> mockLogs = List.of(
                    AuditLog.of("user@example.com", "LOGIN", "Logged in"),
                    AuditLog.of("user@example.com", "REGISTER", "Registered")
            );
            when(auditService.getAllLogs()).thenReturn(mockLogs);

            // ACT
            List<AuditLog> result = authService.getAuditLogs();

            // ASSERT
            assertEquals(2, result.size());
            verify(auditService).getAllLogs();
        }

        @Test
        @DisplayName("Should return empty list when no audit logs exist")
        void getAuditLogs_EmptyList() {
            when(auditService.getAllLogs()).thenReturn(Collections.emptyList());

            List<AuditLog> result = authService.getAuditLogs();

            assertTrue(result.isEmpty());
            verify(auditService).getAllLogs();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  14. sendPasswordResetOtp() — Send OTP email for password reset
    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("sendPasswordResetOtp()")
    class SendPasswordResetOtpTests {

        @Test
        @DisplayName("Should generate OTP, store in Redis with 5-min TTL, and send email")
        void sendPasswordResetOtp_Success() {
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

            // ACT
            authService.sendPasswordResetOtp("test@example.com");

            // VERIFY: OTP stored in Redis with key "PWD_OTP_<email>" and 5-minute TTL
            verify(redisService).setValue(eq("PWD_OTP_test@example.com"), anyString(), eq(5L), eq(TimeUnit.MINUTES));
            // VERIFY: OTP email was sent
            verify(emailService).sendOtpEmail(eq("test@example.com"), anyString());
            // VERIFY: Action was audit-logged
            verify(auditService).log(eq("test@example.com"), eq("PASSWORD_RESET_OTP_SENT"), anyString());
        }

        @Test
        @DisplayName("Should throw exception when user email not found")
        void sendPasswordResetOtp_UserNotFound() {
            when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> authService.sendPasswordResetOtp("missing@example.com"));
            assertEquals("No account found with this email", ex.getMessage());

            // VERIFY: No OTP sent for non-existent user
            verify(emailService, never()).sendOtpEmail(anyString(), anyString());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  15. resetPasswordWithOtp() — Verify OTP and set new password
    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("resetPasswordWithOtp()")
    class ResetPasswordWithOtpTests {

        @Test
        @DisplayName("Should reset password when OTP is valid")
        void resetPasswordWithOtp_Success() {
            // ARRANGE: OTP "123456" is stored in Redis for this email
            when(redisService.getValue("PWD_OTP_test@example.com")).thenReturn("123456");
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
            when(passwordEncoder.encode("newSecurePass")).thenReturn("newEncodedPass");

            // ACT
            authService.resetPasswordWithOtp("test@example.com", "123456", "newSecurePass");

            // ASSERT: Password was updated
            assertEquals("newEncodedPass", testUser.getPasswordHash());
            verify(userRepository).save(testUser);
            // VERIFY: OTP cleaned up from Redis after use
            verify(redisService).deleteValue("PWD_OTP_test@example.com");
            // VERIFY: Success logged in audit trail
            verify(auditService).log(eq("test@example.com"), eq("PASSWORD_RESET_SUCCESS"), anyString());
        }

        @Test
        @DisplayName("Should throw when OTP has expired (null in Redis)")
        void resetPasswordWithOtp_ExpiredOtp() {
            // ARRANGE: No OTP found in Redis → it expired
            when(redisService.getValue("PWD_OTP_test@example.com")).thenReturn(null);

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> authService.resetPasswordWithOtp("test@example.com", "123456", "newPass"));
            assertEquals("OTP has expired. Please request a new one.", ex.getMessage());

            // VERIFY: No password change happens
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("Should throw when OTP does not match")
        void resetPasswordWithOtp_InvalidOtp() {
            // ARRANGE: Stored OTP is "654321" but user entered "000000"
            when(redisService.getValue("PWD_OTP_test@example.com")).thenReturn("654321");

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> authService.resetPasswordWithOtp("test@example.com", "000000", "newPass"));
            assertEquals("Invalid OTP. Please check and try again.", ex.getMessage());

            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("Should throw when user not found after OTP validation")
        void resetPasswordWithOtp_UserNotFound() {
            // ARRANGE: OTP is valid, but user was deleted between OTP request and reset
            when(redisService.getValue("PWD_OTP_gone@example.com")).thenReturn("123456");
            when(userRepository.findByEmail("gone@example.com")).thenReturn(Optional.empty());

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> authService.resetPasswordWithOtp("gone@example.com", "123456", "newPass"));
            assertEquals("User not found", ex.getMessage());
        }
    }
}
