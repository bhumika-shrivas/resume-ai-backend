package com.resumeai.export.service;

import com.resumeai.export.client.AuthClient;
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
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ExportServiceImpl.
 *
 * Covers:
 *  - Quota checks (FREE users limited to 10 PDFs/day, DOCX/JSON are premium only)
 *  - Async job submission (saving QUEUED job and sending to RabbitMQ)
 *  - Job status tracking and appending the download URL
 *  - File download delegation and deletion
 */
@ExtendWith(MockitoExtension.class)
class ExportServiceImplTest {

    @Mock
    private ExportRepository exportRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private AuthClient authClient;

    @InjectMocks
    private ExportServiceImpl exportService;

    private Map<String, Object> freeUserMap;
    private Map<String, Object> premiumUserMap;
    private Map<String, Object> exportData;

    @BeforeEach
    void setUp() {
        // Inject @Value properties using ReflectionTestUtils
        ReflectionTestUtils.setField(exportService, "downloadUrl", "http://api.resumeai.com/download/");
        ReflectionTestUtils.setField(exportService, "exchange", "export.exchange");
        ReflectionTestUtils.setField(exportService, "routingKey", "export.key");

        // Map representing a FREE user from AuthClient
        freeUserMap = new HashMap<>();
        freeUserMap.put("subscriptionPlan", "FREE");

        // Map representing a PREMIUM user
        premiumUserMap = new HashMap<>();
        premiumUserMap.put("subscriptionPlan", "PREMIUM");

        exportData = new HashMap<>();
        exportData.put("templateId", "modern-1");
    }

    // ========================================================================
    //  exportToPdf() Tests
    // ========================================================================
    @Nested
    @DisplayName("exportToPdf()")
    class ExportToPdfTests {

        @Test
        @DisplayName("Should submit PDF job for FREE user within daily limit")
        void exportToPdf_freeUser_success() {
            // ARRANGE: Free user, only 2 exports today (< 10)
            when(authClient.getUserByEmail("free@test.com")).thenReturn(freeUserMap);
            when(exportRepository.countByUserIdToday(eq("free@test.com"), any())).thenReturn(2);
            when(exportRepository.save(any(ExportJob.class))).thenAnswer(inv -> inv.getArgument(0));

            // ACT
            ExportJob job = exportService.exportToPdf(1L, "free@test.com", exportData);

            // ASSERT: Job created correctly
            assertNotNull(job.getJobId());
            assertEquals("PDF", job.getFormat());
            assertEquals("QUEUED", job.getStatus());
            assertEquals("modern-1", job.getTemplateId());

            // VERIFY: Sent to RabbitMQ
            verify(rabbitTemplate).convertAndSend(eq("export.exchange"), eq("export.key"), any(ExportRequest.class));
        }

        @Test
        @DisplayName("Should throw if FREE user exceeds daily PDF limit")
        void exportToPdf_freeUser_limitReached() {
            // ARRANGE: Free user, already hit 10 exports
            when(authClient.getUserByEmail("free@test.com")).thenReturn(freeUserMap);
            when(exportRepository.countByUserIdToday(eq("free@test.com"), any())).thenReturn(10);

            // ACT & ASSERT
            RuntimeException ex = assertThrows(RuntimeException.class, 
                () -> exportService.exportToPdf(1L, "free@test.com", exportData));
            assertTrue(ex.getMessage().contains("Daily PDF export limit"));

            verify(exportRepository, never()).save(any(ExportJob.class));
            verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
        }

        @Test
        @DisplayName("Should allow PREMIUM user to bypass PDF daily limits entirely")
        void exportToPdf_premiumUser_bypassesLimit() {
            // ARRANGE: Premium user
            when(authClient.getUserByEmail("premium@test.com")).thenReturn(premiumUserMap);
            when(exportRepository.save(any(ExportJob.class))).thenAnswer(inv -> inv.getArgument(0));

            // ACT
            exportService.exportToPdf(1L, "premium@test.com", exportData);

            // VERIFY: Usage count is NEVER checked for Premium
            verify(exportRepository, never()).countByUserIdToday(anyString(), any());
            verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(ExportRequest.class));
        }
    }

    // ========================================================================
    //  exportToDocx() / exportToJson() Tests
    // ========================================================================
    @Nested
    @DisplayName("Premium-only Formats (DOCX, JSON)")
    class PremiumFormatTests {

        @Test
        @DisplayName("Should block FREE user from exporting to DOCX")
        void exportToDocx_freeUser_blocked() {
            when(authClient.getUserByEmail("free@test.com")).thenReturn(freeUserMap);

            RuntimeException ex = assertThrows(RuntimeException.class, 
                () -> exportService.exportToDocx(1L, "free@test.com", exportData));
            assertTrue(ex.getMessage().contains("Premium feature"));
        }

        @Test
        @DisplayName("Should allow PREMIUM user to export to DOCX")
        void exportToDocx_premiumUser_success() {
            when(authClient.getUserByEmail("premium@test.com")).thenReturn(premiumUserMap);
            when(exportRepository.save(any(ExportJob.class))).thenAnswer(inv -> inv.getArgument(0));

            ExportJob job = exportService.exportToDocx(1L, "premium@test.com", exportData);
            
            assertEquals("DOCX", job.getFormat());
            verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(ExportRequest.class));
        }

        @Test
        @DisplayName("Should block FREE user from exporting to JSON")
        void exportToJson_freeUser_blocked() {
            when(authClient.getUserByEmail("free@test.com")).thenReturn(freeUserMap);

            assertThrows(RuntimeException.class, 
                () -> exportService.exportToJson(1L, "free@test.com", exportData));
        }
    }

    // ========================================================================
    //  getJobStatus() & getExportsByUser() Tests
    // ========================================================================
    @Nested
    @DisplayName("Job Retrieval and URL Formatting")
    class JobRetrievalTests {

        @Test
        @DisplayName("getJobStatus should append download URL if COMPLETED")
        void getJobStatus_completedJob() {
            // ARRANGE
            ExportJob job = new ExportJob();
            job.setJobId("job-123");
            job.setStatus("COMPLETED");
            job.setFileUrl("path/to/file.pdf"); // Raw local path

            when(exportRepository.findByJobId("job-123")).thenReturn(Optional.of(job));

            // ACT
            Optional<ExportJob> result = exportService.getJobStatus("job-123");

            // ASSERT: The fileUrl has been rewritten to be a full HTTP download link
            assertTrue(result.isPresent());
            assertEquals("http://api.resumeai.com/download/job-123", result.get().getFileUrl());
        }

        @Test
        @DisplayName("getJobStatus should NOT append URL if QUEUED")
        void getJobStatus_queuedJob() {
            ExportJob job = new ExportJob();
            job.setJobId("job-456");
            job.setStatus("QUEUED");
            job.setFileUrl(null); 

            when(exportRepository.findByJobId("job-456")).thenReturn(Optional.of(job));

            Optional<ExportJob> result = exportService.getJobStatus("job-456");

            assertTrue(result.isPresent());
            assertNull(result.get().getFileUrl());
        }
        
        @Test
        @DisplayName("getExportsByUser should append URLs to all COMPLETED jobs")
        void getExportsByUser() {
            ExportJob job1 = new ExportJob();
            job1.setJobId("j1");
            job1.setStatus("COMPLETED");
            job1.setFileUrl("path1");
            
            ExportJob job2 = new ExportJob();
            job2.setJobId("j2");
            job2.setStatus("FAILED");

            when(exportRepository.findByUserId("user-1")).thenReturn(List.of(job1, job2));

            List<ExportJob> results = exportService.getExportsByUser("user-1");

            assertEquals(2, results.size());
            assertEquals("http://api.resumeai.com/download/j1", results.get(0).getFileUrl());
            assertNull(results.get(1).getFileUrl());
        }
    }

    // ========================================================================
    //  downloadFile() & deleteExport() Tests
    // ========================================================================
    @Nested
    @DisplayName("File Management")
    class FileManagementTests {

        @Test
        @DisplayName("downloadFile should fetch bytes from FileStorageService")
        void downloadFile_success() {
            ExportJob job = new ExportJob();
            job.setFileUrl("local/path.pdf");
            when(exportRepository.findByJobId("job-1")).thenReturn(Optional.of(job));
            
            byte[] fileBytes = "fake-pdf-content".getBytes();
            when(fileStorageService.getFile("local/path.pdf")).thenReturn(fileBytes);

            byte[] result = exportService.downloadFile("job-1");
            
            assertArrayEquals(fileBytes, result);
        }

        @Test
        @DisplayName("deleteExport should delete from DB and filesystem")
        void deleteExport_success() {
            ExportJob job = new ExportJob();
            job.setFileUrl("local/path.pdf");
            when(exportRepository.findByJobId("job-1")).thenReturn(Optional.of(job));

            exportService.deleteExport("job-1");

            verify(fileStorageService).deleteFile("local/path.pdf");
            verify(exportRepository).delete(job);
        }
    }
}
