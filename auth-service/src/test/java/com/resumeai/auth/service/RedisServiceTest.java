package com.resumeai.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RedisService.
 *
 * RedisService wraps Spring's StringRedisTemplate to provide:
 *   - setValue()          → stores a key-value pair with TTL (timeout)
 *   - getValue()          → retrieves a value by key
 *   - deleteValue()       → deletes a key
 *   - blacklistToken()    → stores "BL_<token>" = "true" with 24-hour TTL
 *   - isTokenBlacklisted()→ checks if "BL_<token>" exists and equals "true"
 *
 * KEY DESIGN DECISION:
 *   - setValue(), getValue(), deleteValue(), and isTokenBlacklisted() catch Redis exceptions
 *     silently (return null/false) to prevent Redis outages from crashing the app.
 *   - This is tested with the "Redis exception" test cases below.
 *
 * MOCK SETUP:
 *   - We mock StringRedisTemplate and ValueOperations<String, String>
 *   - @BeforeEach wires: redisTemplate.opsForValue() → returns mock valueOperations
 *   - lenient() is used because not all tests call opsForValue() (e.g., deleteValue tests)
 */
@ExtendWith(MockitoExtension.class)
class RedisServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;  // Spring Redis template (mocked)

    @Mock
    private ValueOperations<String, String> valueOperations;  // Redis value operations (mocked)

    @InjectMocks
    private RedisService redisService;  // The class under test

    @BeforeEach
    void setUp() {
        // Wire the mock: redisTemplate.opsForValue() returns our mock valueOperations.
        // lenient() is needed because deleteValue() tests don't call opsForValue(),
        // and Mockito strict mode would otherwise throw UnnecessaryStubbings error.
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // ========================================================================
    //  setValue() Tests
    // ========================================================================
    @Nested
    @DisplayName("setValue()")
    class SetValueTests {

        @Test
        @DisplayName("Should store value in Redis with correct timeout and unit")
        void setValue_success() {
            // ARRANGE
            String key = "session:abc";
            String value = "userData";
            long timeout = 30;
            TimeUnit unit = TimeUnit.MINUTES;

            // ACT: Store the value in Redis
            redisService.setValue(key, value, timeout, unit);

            // ASSERT: Verify the correct Redis operation was called
            verify(redisTemplate).opsForValue();
            verify(valueOperations).set(key, value, timeout, unit);
        }

        @Test
        @DisplayName("Should swallow exception gracefully when Redis is unavailable")
        void setValue_redisException_swallowedGracefully() {
            // ARRANGE: Simulate Redis connection failure
            String key = "session:abc";
            String value = "userData";
            long timeout = 30;
            TimeUnit unit = TimeUnit.MINUTES;

            doThrow(new RedisConnectionFailureException("Connection refused"))
                    .when(valueOperations).set(key, value, timeout, unit);

            // ACT & ASSERT: No exception should propagate to the caller
            // This ensures the app doesn't crash when Redis is down
            assertDoesNotThrow(() -> redisService.setValue(key, value, timeout, unit));
            verify(valueOperations).set(key, value, timeout, unit);
        }
    }

    // ========================================================================
    //  getValue() Tests
    // ========================================================================
    @Nested
    @DisplayName("getValue()")
    class GetValueTests {

        @Test
        @DisplayName("Should return stored value from Redis")
        void getValue_success_returnsValue() {
            // ARRANGE
            String key = "session:abc";
            String expectedValue = "userData";
            when(valueOperations.get(key)).thenReturn(expectedValue);

            // ACT
            String result = redisService.getValue(key);

            // ASSERT: Returns the stored value
            assertEquals(expectedValue, result);
            verify(redisTemplate).opsForValue();
            verify(valueOperations).get(key);
        }

        @Test
        @DisplayName("Should return null when key does not exist")
        void getValue_keyNotFound_returnsNull() {
            // ARRANGE: Key doesn't exist in Redis → returns null
            when(valueOperations.get("nonexistent")).thenReturn(null);

            // ACT
            String result = redisService.getValue("nonexistent");

            // ASSERT: null means "key not found"
            assertNull(result);
            verify(valueOperations).get("nonexistent");
        }

        @Test
        @DisplayName("Should return null when Redis throws an exception")
        void getValue_redisException_returnsNull() {
            // ARRANGE: Redis connection failure
            when(valueOperations.get("session:abc"))
                    .thenThrow(new RedisConnectionFailureException("Connection refused"));

            // ACT: Should return null instead of crashing
            String result = redisService.getValue("session:abc");

            // ASSERT: Graceful degradation — returns null on error
            assertNull(result);
            verify(valueOperations).get("session:abc");
        }
    }

    // ========================================================================
    //  deleteValue() Tests
    // ========================================================================
    @Nested
    @DisplayName("deleteValue()")
    class DeleteValueTests {

        @Test
        @DisplayName("Should delete key from Redis successfully")
        void deleteValue_success() {
            // ARRANGE
            String key = "session:abc";
            when(redisTemplate.delete(key)).thenReturn(Boolean.TRUE);

            // ACT: Delete the key
            redisService.deleteValue(key);

            // ASSERT: redisTemplate.delete() was called (not opsForValue!)
            verify(redisTemplate).delete(key);
        }

        @Test
        @DisplayName("Should swallow exception gracefully when Redis is unavailable")
        void deleteValue_redisException_swallowedGracefully() {
            // ARRANGE: Simulate Redis failure on delete
            String key = "session:abc";
            when(redisTemplate.delete(key))
                    .thenThrow(new RedisConnectionFailureException("Connection refused"));

            // ACT & ASSERT: Should not throw — graceful degradation
            assertDoesNotThrow(() -> redisService.deleteValue(key));
            verify(redisTemplate).delete(key);
        }
    }

    // ========================================================================
    //  blacklistToken() Tests
    // ========================================================================
    @Nested
    @DisplayName("blacklistToken()")
    class BlacklistTokenTests {

        @Test
        @DisplayName("Should store token with BL_ prefix, value 'true', and 24-hour TTL")
        void blacklistToken_setsCorrectKeyPrefixAndTTL() {
            // ARRANGE
            String token = "jwt-token-xyz";

            // ACT: Blacklist the token
            redisService.blacklistToken(token);

            // ASSERT: Stored as "BL_jwt-token-xyz" = "true" with 24-hour TTL
            // This allows checking if a JWT has been revoked (after logout)
            verify(redisTemplate).opsForValue();
            verify(valueOperations).set("BL_" + token, "true", 24, TimeUnit.HOURS);
        }
    }

    // ========================================================================
    //  isTokenBlacklisted() Tests
    // ========================================================================
    @Nested
    @DisplayName("isTokenBlacklisted()")
    class IsTokenBlacklistedTests {

        @Test
        @DisplayName("Should return true when token is blacklisted")
        void isTokenBlacklisted_tokenExists_returnsTrue() {
            // ARRANGE: Token was blacklisted (value = "true")
            String token = "jwt-token-xyz";
            when(valueOperations.get("BL_" + token)).thenReturn("true");

            // ACT
            boolean result = redisService.isTokenBlacklisted(token);

            // ASSERT: Token IS blacklisted
            assertTrue(result);
            verify(valueOperations).get("BL_" + token);
        }

        @Test
        @DisplayName("Should return false when token is not blacklisted (key absent)")
        void isTokenBlacklisted_tokenAbsent_returnsFalse() {
            // ARRANGE: Token key doesn't exist in Redis
            String token = "jwt-token-xyz";
            when(valueOperations.get("BL_" + token)).thenReturn(null);

            // ACT
            boolean result = redisService.isTokenBlacklisted(token);

            // ASSERT: Token is NOT blacklisted (null → false)
            assertFalse(result);
            verify(valueOperations).get("BL_" + token);
        }

        @Test
        @DisplayName("Should return false when Redis value is not 'true'")
        void isTokenBlacklisted_unexpectedValue_returnsFalse() {
            // ARRANGE: Value exists but is not exactly "true"
            String token = "jwt-token-xyz";
            when(valueOperations.get("BL_" + token)).thenReturn("false");

            // ACT
            boolean result = redisService.isTokenBlacklisted(token);

            // ASSERT: Only "true" counts as blacklisted
            assertFalse(result);
            verify(valueOperations).get("BL_" + token);
        }

        @Test
        @DisplayName("Should return false when Redis throws an exception")
        void isTokenBlacklisted_redisException_returnsFalse() {
            // ARRANGE: Redis is down
            String token = "jwt-token-xyz";
            when(valueOperations.get("BL_" + token))
                    .thenThrow(new RedisConnectionFailureException("Connection refused"));

            // ACT: Should return false (fail-open for blacklist checks)
            boolean result = redisService.isTokenBlacklisted(token);

            // ASSERT: Graceful degradation — treat as not blacklisted
            assertFalse(result);
            verify(valueOperations).get("BL_" + token);
        }
    }
}
