package com.resumeai.notification.service;

import com.resumeai.notification.entity.Notification;
import com.resumeai.notification.repository.NotificationRepository;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for NotificationServiceImpl.
 * 
 * Testing Focus:
 *  - DB operations (save, mark as read, delete).
 *  - Email dispatch logic (verifying JavaMailSender is called when appropriate).
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private JavaMailSender emailSender;

    @Mock
    private MimeMessage mimeMessage;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    private Notification sampleNotif;

    @BeforeEach
    void setUp() {
        // Inject @Value property for fromEmail to prevent MimeMessageHelper from throwing an exception
        org.springframework.test.util.ReflectionTestUtils.setField(notificationService, "fromEmail", "noreply@resumeai.com");

        sampleNotif = new Notification();
        sampleNotif.setNotificationId(1L);
        sampleNotif.setRecipientId("user@test.com");
        sampleNotif.setTitle("Test Title");
        sampleNotif.setMessage("Test Message");
        sampleNotif.setChannel("IN_APP");
        sampleNotif.setRead(false);
    }

    // ========================================================================
    //  send() Tests
    // ========================================================================
    @Nested
    @DisplayName("send()")
    class SendTests {

        @Test
        @DisplayName("Should save to DB but NOT send email if channel is IN_APP")
        void send_inAppOnly() {
            // ACT
            notificationService.send(sampleNotif);

            // ASSERT
            verify(notificationRepository).save(sampleNotif);
            verify(emailSender, never()).send(any(MimeMessage.class));
        }

        @Test
        @DisplayName("Should save to DB and send email if channel is EMAIL")
        void send_emailOnly() {
            // ARRANGE
            sampleNotif.setChannel("EMAIL");
            when(emailSender.createMimeMessage()).thenReturn(mimeMessage);

            // ACT
            notificationService.send(sampleNotif);

            // ASSERT
            verify(notificationRepository).save(sampleNotif);
            verify(emailSender).send(mimeMessage);
        }

        @Test
        @DisplayName("Should save to DB and send email if channel is BOTH")
        void send_bothChannels() {
            // ARRANGE
            sampleNotif.setChannel("BOTH");
            when(emailSender.createMimeMessage()).thenReturn(mimeMessage);

            // ACT
            notificationService.send(sampleNotif);

            // ASSERT
            verify(notificationRepository).save(sampleNotif);
            verify(emailSender).send(mimeMessage);
        }
    }

    // ========================================================================
    //  Status Update Tests
    // ========================================================================
    @Nested
    @DisplayName("Read Status & Deletion")
    class StatusTests {

        @Test
        @DisplayName("markAsRead should update flag and save")
        void markAsRead_success() {
            when(notificationRepository.findById(1L)).thenReturn(Optional.of(sampleNotif));

            notificationService.markAsRead(1L);

            assertTrue(sampleNotif.isRead());
            verify(notificationRepository).save(sampleNotif);
        }

        @Test
        @DisplayName("markAllRead should fetch unread and update all")
        void markAllRead_success() {
            Notification n2 = new Notification(); n2.setRead(false);
            
            when(notificationRepository.findByRecipientIdAndIsReadOrderBySentAtDesc("user@test.com", false))
                .thenReturn(List.of(sampleNotif, n2));

            notificationService.markAllRead("user@test.com");

            assertTrue(sampleNotif.isRead());
            assertTrue(n2.isRead());
            verify(notificationRepository).saveAll(anyIterable());
        }

        @Test
        @DisplayName("deleteNotification should delegate to repository")
        void deleteNotification_success() {
            notificationService.deleteNotification(1L);
            verify(notificationRepository).deleteById(1L);
        }
    }

    // ========================================================================
    //  Bulk Dispatch Tests
    // ========================================================================
    @Nested
    @DisplayName("Bulk Operations")
    class BulkTests {

        @Test
        @DisplayName("sendBulk should save BROADCAST records for all and trigger emails")
        void sendBulk_success() {
            // ARRANGE
            List<String> recipients = List.of("user1@test.com", "user2@test.com");
            when(emailSender.createMimeMessage()).thenReturn(mimeMessage);

            // ACT
            notificationService.sendBulk(recipients, "Promo", "50% Off!");

            // ASSERT
            ArgumentCaptor<Notification> notifCaptor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository, times(2)).save(notifCaptor.capture());
            
            List<Notification> saved = notifCaptor.getAllValues();
            assertEquals("BROADCAST", saved.get(0).getType());
            assertEquals("BOTH", saved.get(0).getChannel());
            
            // Should attempt to send 2 emails
            verify(emailSender, times(2)).send(mimeMessage);
        }
    }
}
