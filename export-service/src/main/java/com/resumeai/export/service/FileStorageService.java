package com.resumeai.export.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

@Service
public class FileStorageService {

    @Value("${storage.root}")
    private String storageRoot;

    private Path rootPath;

    @PostConstruct
    public void init() {
        try {
            if (storageRoot != null) {
                storageRoot = storageRoot.trim();
            }
            rootPath = Paths.get(storageRoot);
            if (!Files.exists(rootPath)) {
                Files.createDirectories(rootPath);
                System.out.println("Created export storage root at: " + rootPath.toAbsolutePath());
            } else {
                System.out.println("Using existing export storage root at: " + rootPath.toAbsolutePath());
            }
        } catch (IOException e) {
            System.err.println("CRITICAL: Could not initialize storage location: " + storageRoot);
            throw new RuntimeException("Could not initialize storage location", e);
        }
    }

    public String saveFile(String subPath, String fileName, byte[] content) {
        try {
            Path directoryPath = rootPath.resolve(subPath);
            if (!Files.exists(directoryPath)) {
                Files.createDirectories(directoryPath);
            }

            Path filePath = directoryPath.resolve(fileName);
            Files.write(filePath, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            
            // Return the relative path to be stored in the DB
            return subPath + "/" + fileName;
        } catch (IOException e) {
            throw new RuntimeException("Could not save file to local system", e);
        }
    }

    public byte[] getFile(String relativePath) {
        try {
            Path filePath = rootPath.resolve(relativePath);
            if (!Files.exists(filePath)) {
                throw new RuntimeException("File not found: " + relativePath);
            }
            return Files.readAllBytes(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Could not read file from local system", e);
        }
    }

    public void deleteFile(String relativePath) {
        try {
            Path filePath = rootPath.resolve(relativePath);
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Could not delete file from local system", e);
        }
    }
}
