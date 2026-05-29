package com.resumeai.export.service;

import com.resumeai.export.client.NotificationClient;
import com.resumeai.export.dto.ExportRequest;
import com.resumeai.export.entity.ExportJob;
import com.resumeai.export.repository.ExportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ExportListener.
 *
 * This class consumes messages from RabbitMQ and processes the actual file generation.
 * It uses iText for PDF, Apache POI for DOCX, and simple toString() for JSON.
 * 
 * Testing Focus:
 *  - Ensuring job status transitions correctly (QUEUED -> PROCESSING -> COMPLETED/FAILED)
 *  - Verifying the generated file is passed to FileStorageService
 *  - Verifying a notification is sent on success, but doesn't fail the job if the notification service is down
 */
@ExtendWith(MockitoExtension.class)
class ExportListenerTest {

    @Mock
    private ExportRepository exportRepository;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private NotificationClient notificationClient;

    @InjectMocks
    private ExportListener exportListener;

    private ExportJob sampleJob;
    private ExportRequest sampleRequest;
    private Map<String, Object> exportData;

    @BeforeEach
    void setUp() {
        sampleJob = new ExportJob();
        sampleJob.setJobId("job-123");
        sampleJob.setUserId("user-456");
        sampleJob.setStatus("QUEUED");

        exportData = new HashMap<>();
        
        sampleRequest = new ExportRequest();
        sampleRequest.setJobId("job-123");
        sampleRequest.setFormat("JSON");
        sampleRequest.setData(exportData);
    }

    @Nested
    @DisplayName("processExport()")
    class ProcessExportTests {

        @Test
        @DisplayName("Should return early if job is not found in database")
        void processExport_jobNotFound() {
            // ARRANGE
            when(exportRepository.findById("job-123")).thenReturn(Optional.empty());

            // ACT
            exportListener.processExport(sampleRequest);

            // ASSERT: Should do nothing else
            verify(exportRepository, never()).save(any(ExportJob.class));
            verify(fileStorageService, never()).saveFile(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("Should process JSON successfully, update status, and send notification")
        void processExport_jsonFormat_success() {
            // ARRANGE
            exportData.put("key", "value"); // Simple data for JSON
            when(exportRepository.findById("job-123")).thenReturn(Optional.of(sampleJob));
            
            // Mock storage service to return a dummy relative path
            when(fileStorageService.saveFile(eq("resumes/user-456"), eq("job-123.json"), any()))
                .thenReturn("resumes/user-456/job-123.json");

            // ACT
            exportListener.processExport(sampleRequest);

            // ASSERT: Job status should be COMPLETED
            assertEquals("COMPLETED", sampleJob.getStatus());
            assertEquals("resumes/user-456/job-123.json", sampleJob.getFileUrl());
            assertNotNull(sampleJob.getCompletedAt());
            assertNotNull(sampleJob.getExpiresAt());

            // VERIFY: Saved twice (once for PROCESSING, once for COMPLETED)
            verify(exportRepository, times(2)).save(sampleJob);
            
            // VERIFY: Notification was sent
            ArgumentCaptor<Map<String, Object>> notifCaptor = ArgumentCaptor.forClass(Map.class);
            verify(notificationClient).sendNotification(notifCaptor.capture());
            
            Map<String, Object> notif = notifCaptor.getValue();
            assertEquals("user-456", notif.get("recipientId"));
            assertEquals("EXPORT_READY", notif.get("type"));
        }

        @Test
        @DisplayName("Should complete job successfully even if notification service fails")
        void processExport_notificationFails_jobStillCompleted() {
            // ARRANGE
            sampleRequest.setFormat("JSON");
            when(exportRepository.findById("job-123")).thenReturn(Optional.of(sampleJob));
            when(fileStorageService.saveFile(anyString(), anyString(), any())).thenReturn("path.json");
            
            // Simulate notification service throwing exception
            doThrow(new RuntimeException("Notification service down")).when(notificationClient).sendNotification(anyMap());

            // ACT
            exportListener.processExport(sampleRequest);

            // ASSERT: Job should STILL be completed despite notification failure
            assertEquals("COMPLETED", sampleJob.getStatus());
            verify(exportRepository, times(2)).save(sampleJob);
        }

        @Test
        @DisplayName("Should fail job if an unsupported format is requested")
        void processExport_unsupportedFormat_failsJob() {
            // ARRANGE
            sampleRequest.setFormat("UNKNOWN_FORMAT");
            when(exportRepository.findById("job-123")).thenReturn(Optional.of(sampleJob));

            // ACT
            exportListener.processExport(sampleRequest);

            // ASSERT: Exception was thrown internally, caught, and job status set to FAILED
            assertEquals("FAILED", sampleJob.getStatus());
            
            // VERIFY: Saved twice (once for PROCESSING, once for FAILED)
            verify(exportRepository, times(2)).save(sampleJob);
            
            // VERIFY: File was never saved and notification never sent
            verify(fileStorageService, never()).saveFile(anyString(), anyString(), any());
            verify(notificationClient, never()).sendNotification(anyMap());
        }

        @Test
        @DisplayName("Should process PDF successfully")
        void processExport_pdfFormat_success() {
            // ARRANGE
            sampleRequest.setFormat("PDF");
            exportData.put("html", "<html><body>Hello</body></html>"); // Minimal HTML for iText
            
            when(exportRepository.findById("job-123")).thenReturn(Optional.of(sampleJob));
            when(fileStorageService.saveFile(eq("resumes/user-456"), eq("job-123.pdf"), any())).thenReturn("path.pdf");

            // ACT
            exportListener.processExport(sampleRequest);

            // ASSERT
            assertEquals("COMPLETED", sampleJob.getStatus());
            verify(fileStorageService).saveFile(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("Should process DOCX successfully")
        void processExport_docxFormat_success() {
            // ARRANGE
            sampleRequest.setFormat("DOCX");
            exportData.put("text", "Simple text for docx"); 
            
            when(exportRepository.findById("job-123")).thenReturn(Optional.of(sampleJob));
            when(fileStorageService.saveFile(eq("resumes/user-456"), eq("job-123.docx"), any())).thenReturn("path.docx");

            // ACT
            exportListener.processExport(sampleRequest);

            // ASSERT
            assertEquals("COMPLETED", sampleJob.getStatus());
            verify(fileStorageService).saveFile(anyString(), anyString(), any());
        }
    }
}
