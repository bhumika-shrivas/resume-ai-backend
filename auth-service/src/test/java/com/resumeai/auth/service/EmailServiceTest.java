package com.resumeai.auth.service;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EmailService.
 *
 * EmailService sends various emails using JavaMailSender:
 *   - sendOtpEmail()           → sends password reset OTP (throws on failure)
 *   - sendPlanChangeEmail()    → notifies plan upgrade/downgrade (swallows exceptions)
 *   - sendAccountStatusEmail() → notifies account suspend/reactivate (swallows exceptions)
 *   - sendRoleChangeEmail()    → notifies role change (swallows exceptions)
 *   - sendAccountDeletedEmail()→ notifies account deletion (swallows exceptions)
 *
 * KEY DESIGN NOTE:
 *   - sendOtpEmail() is the ONLY method that propagates exceptions (RuntimeException)
 *   - All other methods swallow exceptions silently (async/notification emails)
 *   - We test this behavior explicitly in the "failure" tests
 *
 * MOCK SETUP:
 *   - JavaMailSender is mocked — no real SMTP connection
 *   - MimeMessage is mocked — we don't verify email content, just that send() was called
 *   - fromEmail is set via ReflectionTestUtils (it's @Value-injected in real code)
 */
@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;  // Spring's email sender (mocked)

    @Mock
    private MimeMessage mimeMessage;  // The email message object (mocked)

    @InjectMocks
    private EmailService emailService;  // The class under test

    @BeforeEach
    void setUp() {
        // Inject the "from" email address that's normally set via @Value("${spring.mail.username}")
        // Without this, the real EmailService would have a null fromEmail field
        ReflectionTestUtils.setField(emailService, "fromEmail", "test@resumeai.com");

        // Every test needs createMimeMessage() to return our mock
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
    }

    // ========================================================================
    //  sendOtpEmail() Tests
    // ========================================================================
    @Nested
    @DisplayName("sendOtpEmail")
    class SendOtpEmailTests {

        @Test
        @DisplayName("should send OTP email successfully")
        void sendOtpEmail_success() {
            // ARRANGE
            String to = "user@example.com";
            String otp = "123456";

            // ACT & ASSERT: Should not throw any exception
            assertDoesNotThrow(() -> emailService.sendOtpEmail(to, otp));

            // VERIFY: MimeMessage was created and sent
            verify(mailSender).createMimeMessage();
            verify(mailSender).send(any(MimeMessage.class));
        }

        @Test
        @DisplayName("should throw RuntimeException when mailSender.send fails")
        void sendOtpEmail_failure_throwsRuntimeException() {
            // ARRANGE: Simulate SMTP failure
            String to = "user@example.com";
            String otp = "123456";
            doThrow(new RuntimeException("SMTP error")).when(mailSender).send(any(MimeMessage.class));

            // ACT & ASSERT: sendOtpEmail DOES propagate exceptions (unlike other methods)
            RuntimeException exception = assertThrows(RuntimeException.class,
                    () -> emailService.sendOtpEmail(to, otp));

            // The exception message is wrapped with a user-friendly message
            assertEquals("Failed to send OTP email. Please try again.", exception.getMessage());
            verify(mailSender).createMimeMessage();
            verify(mailSender).send(any(MimeMessage.class));
        }
    }

    // ========================================================================
    //  sendPlanChangeEmail() Tests
    // ========================================================================
    @Nested
    @DisplayName("sendPlanChangeEmail")
    class SendPlanChangeEmailTests {

        @Test
        @DisplayName("should send plan change email successfully")
        void sendPlanChangeEmail_success() {
            // ACT: Notify user about plan upgrade
            emailService.sendPlanChangeEmail("user@example.com", "PREMIUM");

            // VERIFY: Email was sent
            verify(mailSender).createMimeMessage();
            verify(mailSender).send(any(MimeMessage.class));
        }

        @Test
        @DisplayName("should swallow exception silently on failure")
        void sendPlanChangeEmail_failure_exceptionSwallowed() {
            // ARRANGE: SMTP fails
            doThrow(new RuntimeException("SMTP error")).when(mailSender).send(any(MimeMessage.class));

            // ACT & ASSERT: Exception is swallowed (notification emails are best-effort)
            assertDoesNotThrow(() -> emailService.sendPlanChangeEmail("user@example.com", "PREMIUM"));

            verify(mailSender).createMimeMessage();
            verify(mailSender).send(any(MimeMessage.class));
        }
    }

    // ========================================================================
    //  sendAccountStatusEmail() Tests
    // ========================================================================
    @Nested
    @DisplayName("sendAccountStatusEmail")
    class SendAccountStatusEmailTests {

        @Test
        @DisplayName("should send account suspended email successfully")
        void sendAccountStatusEmail_suspended_success() {
            // ACT: true = suspended
            emailService.sendAccountStatusEmail("user@example.com", true);

            // VERIFY
            verify(mailSender).createMimeMessage();
            verify(mailSender).send(any(MimeMessage.class));
        }

        @Test
        @DisplayName("should send account reactivated email successfully")
        void sendAccountStatusEmail_reactivated_success() {
            // ACT: false = reactivated
            emailService.sendAccountStatusEmail("user@example.com", false);

            // VERIFY
            verify(mailSender).createMimeMessage();
            verify(mailSender).send(any(MimeMessage.class));
        }

        @Test
        @DisplayName("should swallow exception silently on failure")
        void sendAccountStatusEmail_failure_exceptionSwallowed() {
            // ARRANGE
            doThrow(new RuntimeException("SMTP error")).when(mailSender).send(any(MimeMessage.class));

            // ACT & ASSERT: No exception propagated
            assertDoesNotThrow(() -> emailService.sendAccountStatusEmail("user@example.com", true));

            verify(mailSender).createMimeMessage();
            verify(mailSender).send(any(MimeMessage.class));
        }
    }

    // ========================================================================
    //  sendRoleChangeEmail() Tests
    // ========================================================================
    @Nested
    @DisplayName("sendRoleChangeEmail")
    class SendRoleChangeEmailTests {

        @Test
        @DisplayName("should send role change email successfully")
        void sendRoleChangeEmail_success() {
            // ACT
            emailService.sendRoleChangeEmail("user@example.com", "ADMIN");

            // VERIFY
            verify(mailSender).createMimeMessage();
            verify(mailSender).send(any(MimeMessage.class));
        }

        @Test
        @DisplayName("should swallow exception silently on failure")
        void sendRoleChangeEmail_failure_exceptionSwallowed() {
            // ARRANGE
            doThrow(new RuntimeException("SMTP error")).when(mailSender).send(any(MimeMessage.class));

            // ACT & ASSERT
            assertDoesNotThrow(() -> emailService.sendRoleChangeEmail("user@example.com", "ADMIN"));

            verify(mailSender).createMimeMessage();
            verify(mailSender).send(any(MimeMessage.class));
        }
    }

    // ========================================================================
    //  sendAccountDeletedEmail() Tests
    // ========================================================================
    @Nested
    @DisplayName("sendAccountDeletedEmail")
    class SendAccountDeletedEmailTests {

        @Test
        @DisplayName("should send account deleted email successfully")
        void sendAccountDeletedEmail_success() {
            // ACT
            emailService.sendAccountDeletedEmail("user@example.com");

            // VERIFY
            verify(mailSender).createMimeMessage();
            verify(mailSender).send(any(MimeMessage.class));
        }

        @Test
        @DisplayName("should swallow exception silently on failure")
        void sendAccountDeletedEmail_failure_exceptionSwallowed() {
            // ARRANGE
            doThrow(new RuntimeException("SMTP error")).when(mailSender).send(any(MimeMessage.class));

            // ACT & ASSERT
            assertDoesNotThrow(() -> emailService.sendAccountDeletedEmail("user@example.com"));

            verify(mailSender).createMimeMessage();
            verify(mailSender).send(any(MimeMessage.class));
        }
    }
}
