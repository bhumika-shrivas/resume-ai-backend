package com.resumeai.export.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FileStorageService.
 *
 * This test uses JUnit 5's @TempDir to create a temporary folder that acts as
 * the "storage root" for the test. This ensures that the test safely writes
 * and reads real files from the disk without leaving a mess on the host machine.
 */
class FileStorageServiceTest {

    private FileStorageService fileStorageService;

    // JUnit injects a temporary directory here. It gets deleted automatically after tests.
    @TempDir
    Path tempStorageRoot;

    @BeforeEach
    void setUp() {
        fileStorageService = new FileStorageService();
        
        // Inject the @TempDir absolute path as the storage.root property
        ReflectionTestUtils.setField(fileStorageService, "storageRoot", tempStorageRoot.toAbsolutePath().toString());
        
        // Call init() to simulate @PostConstruct
        fileStorageService.init();
    }

    @Test
    @DisplayName("init() should create the root directory if it does not exist")
    void init_createsDirectory() throws IOException {
        // ARRANGE: Point to a sub-folder inside the temp dir that doesn't exist yet
        Path newRoot = tempStorageRoot.resolve("new-folder");
        ReflectionTestUtils.setField(fileStorageService, "storageRoot", newRoot.toAbsolutePath().toString());

        // ACT
        fileStorageService.init();

        // ASSERT
        assertTrue(Files.exists(newRoot));
        assertTrue(Files.isDirectory(newRoot));
    }

    @Test
    @DisplayName("saveFile() should save bytes to disk and return relative path")
    void saveFile_success() throws IOException {
        // ARRANGE
        String subPath = "user-123";
        String fileName = "resume.pdf";
        byte[] content = "test-pdf-content".getBytes();

        // ACT
        String resultPath = fileStorageService.saveFile(subPath, fileName, content);

        // ASSERT: Should return "user-123/resume.pdf"
        assertEquals("user-123/resume.pdf", resultPath);

        // VERIFY ON DISK: Check if the file was actually written to the temp directory
        Path expectedFile = tempStorageRoot.resolve(subPath).resolve(fileName);
        assertTrue(Files.exists(expectedFile));
        
        byte[] readContent = Files.readAllBytes(expectedFile);
        assertArrayEquals(content, readContent);
    }

    @Test
    @DisplayName("getFile() should read bytes from existing file")
    void getFile_success() throws IOException {
        // ARRANGE: Manually create a file in the temp directory
        Path targetDir = tempStorageRoot.resolve("user-456");
        Files.createDirectories(targetDir);
        Path targetFile = targetDir.resolve("my-export.docx");
        
        byte[] expectedContent = "mock-docx-data".getBytes();
        Files.write(targetFile, expectedContent);

        // ACT
        byte[] retrievedContent = fileStorageService.getFile("user-456/my-export.docx");

        // ASSERT
        assertArrayEquals(expectedContent, retrievedContent);
    }

    @Test
    @DisplayName("getFile() should throw RuntimeException if file is missing")
    void getFile_fileNotFound() {
        RuntimeException ex = assertThrows(RuntimeException.class, 
            () -> fileStorageService.getFile("does-not-exist/missing.pdf"));
        
        assertTrue(ex.getMessage().contains("File not found"));
    }

    @Test
    @DisplayName("deleteFile() should remove file from disk safely")
    void deleteFile_success() throws IOException {
        // ARRANGE: Manually create a file
        Path targetDir = tempStorageRoot.resolve("user-789");
        Files.createDirectories(targetDir);
        Path targetFile = targetDir.resolve("delete-me.json");
        Files.write(targetFile, "{}".getBytes());

        assertTrue(Files.exists(targetFile));

        // ACT
        fileStorageService.deleteFile("user-789/delete-me.json");

        // ASSERT: File should be gone
        assertFalse(Files.exists(targetFile));
    }
}
