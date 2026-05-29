package com.resumeai.auth.service;

import com.resumeai.auth.entity.AuditLog;
import com.resumeai.auth.repository.AuditLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuditService.
 *
 * AuditService has 2 methods:
 *   - log(userId, action, details) → creates an AuditLog entity and saves it to the database
 *   - getAllLogs()                 → returns all audit logs from the database
 *
 * We mock AuditLogRepository to avoid hitting a real database.
 * We use ArgumentCaptor to inspect the AuditLog object that was passed to save().
 */
@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;  // Mocked database access

    @InjectMocks
    private AuditService auditService;  // The class under test

    // ArgumentCaptor captures the argument passed to save() so we can inspect it
    @Captor
    private ArgumentCaptor<AuditLog> auditLogCaptor;

    // ───────────────────────────────────────────────
    //  log() Tests
    // ───────────────────────────────────────────────

    @Test
    @DisplayName("log() should create an AuditLog with correct fields and save it")
    void log_shouldSaveAuditLogWithCorrectFields() {
        // ARRANGE: Define the inputs
        String userId = "user-42";
        String action = "LOGIN";
        String details = "User logged in from Chrome";

        // ACT: Call the method
        auditService.log(userId, action, details);

        // ASSERT: Capture the AuditLog object that was passed to save()
        verify(auditLogRepository, times(1)).save(auditLogCaptor.capture());

        // Inspect the captured object to ensure fields were set correctly
        AuditLog captured = auditLogCaptor.getValue();
        assertNotNull(captured, "Saved AuditLog should not be null");
        assertEquals(userId, captured.getUserId(), "userId should match");
        assertEquals(action, captured.getAction(), "action should match");
        assertEquals(details, captured.getDetails(), "details should match");
    }

    @Test
    @DisplayName("log() should pass null-safe values when arguments are null")
    void log_shouldHandleNullArguments() {
        // ACT: Pass all nulls — method should not crash
        auditService.log(null, null, null);

        // ASSERT: An AuditLog was still created and saved (just with null fields)
        verify(auditLogRepository, times(1)).save(auditLogCaptor.capture());

        AuditLog captured = auditLogCaptor.getValue();
        assertNotNull(captured, "AuditLog object itself should still be created");
        assertNull(captured.getUserId(), "userId should be null");
        assertNull(captured.getAction(), "action should be null");
        assertNull(captured.getDetails(), "details should be null");
    }

    @Test
    @DisplayName("log() should not interact with repository beyond save()")
    void log_shouldOnlyCallSave() {
        // ACT
        auditService.log("u1", "SIGNUP", "new account");

        // ASSERT: only save() was called — no findAll(), findById(), etc.
        // verify(only()) ensures save() was the ONLY method called on the mock
        verify(auditLogRepository, only()).save(any(AuditLog.class));
    }

    // ───────────────────────────────────────────────
    //  getAllLogs() Tests
    // ───────────────────────────────────────────────

    @Test
    @DisplayName("getAllLogs() should return the list provided by the repository")
    void getAllLogs_shouldReturnListFromRepository() {
        // ARRANGE: Create 2 mock AuditLog entries
        AuditLog log1 = AuditLog.builder()
                .id(1L)
                .userId("user-1")
                .action("LOGIN")
                .details("details-1")
                .createdAt(LocalDateTime.of(2026, 5, 21, 10, 0))
                .build();

        AuditLog log2 = AuditLog.builder()
                .id(2L)
                .userId("user-2")
                .action("LOGOUT")
                .details("details-2")
                .createdAt(LocalDateTime.of(2026, 5, 21, 11, 0))
                .build();

        List<AuditLog> expectedLogs = List.of(log1, log2);
        when(auditLogRepository.findAll()).thenReturn(expectedLogs);

        // ACT
        List<AuditLog> result = auditService.getAllLogs();

        // ASSERT: Should return the exact same list object from the repository
        assertNotNull(result, "Returned list should not be null");
        assertEquals(2, result.size(), "Should return exactly 2 logs");
        assertSame(expectedLogs, result, "Should return the exact list instance from the repository");
        assertEquals("user-1", result.get(0).getUserId());
        assertEquals("LOGOUT", result.get(1).getAction());

        verify(auditLogRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("getAllLogs() should return an empty list when no logs exist")
    void getAllLogs_shouldReturnEmptyListWhenNoLogs() {
        // ARRANGE: No logs in the database
        when(auditLogRepository.findAll()).thenReturn(Collections.emptyList());

        // ACT
        List<AuditLog> result = auditService.getAllLogs();

        // ASSERT: Empty list, not null
        assertNotNull(result, "Returned list should not be null");
        assertTrue(result.isEmpty(), "Returned list should be empty");

        verify(auditLogRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("getAllLogs() should not invoke save()")
    void getAllLogs_shouldNotCallSave() {
        // ARRANGE
        when(auditLogRepository.findAll()).thenReturn(Collections.emptyList());

        // ACT
        auditService.getAllLogs();

        // ASSERT: Only findAll() called, save() was NEVER called
        // This ensures read operations don't accidentally write to the database
        verify(auditLogRepository, never()).save(any());
        verify(auditLogRepository, times(1)).findAll();
    }
}
