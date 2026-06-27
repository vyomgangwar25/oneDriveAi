package com.example.demo.service;

import com.example.demo.exception.InvalidFileException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

@Service
public class LocalFileStorageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "image/png",
            "image/jpeg",
            "image/jpg",
            "image/gif",
            "text/plain",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    private final Path basePath;

    public LocalFileStorageService(@Value("${app.storage.base-path:uploads}") String basePath) {
        this.basePath = Paths.get(basePath).toAbsolutePath().normalize();
    }

    public String store(Long userId, UUID fileId, MultipartFile file) throws IOException {
        validateFile(file);

        String sanitizedName = sanitizeFilename(file.getOriginalFilename());
        Path userDir = basePath.resolve("users").resolve(userId.toString());
        Files.createDirectories(userDir);

        String storedFileName = fileId + "_" + sanitizedName;
        Path target = userDir.resolve(storedFileName).normalize();

        if (!target.startsWith(userDir)) {
            throw new InvalidFileException("Invalid file path");
        }

        file.transferTo(target);

        return basePath.relativize(target).toString().replace("\\", "/");
    }

    public Resource loadAsResource(String storagePath) throws IOException {
        Path filePath = basePath.resolve(storagePath).normalize();

        if (!filePath.startsWith(basePath)) {
            throw new InvalidFileException("Invalid file path");
        }

        if (!Files.exists(filePath)) {
            throw new IOException("File not found on disk");
        }

        Resource resource = new UrlResource(filePath.toUri());
        if (!resource.exists() || !resource.isReadable()) {
            throw new IOException("File not readable");
        }

        return resource;
    }

    public void delete(String storagePath) throws IOException {
        Path filePath = basePath.resolve(storagePath).normalize();

        if (!filePath.startsWith(basePath)) {
            throw new InvalidFileException("Invalid file path");
        }

        Files.deleteIfExists(filePath);
    }


    public byte[] readChunk(String storagePath, long offset, int length) throws IOException {
        Path filePath = basePath.resolve(storagePath).normalize();

        if (!filePath.startsWith(basePath)) {
            throw new InvalidFileException("Invalid file path");
        }

        if (!Files.exists(filePath)) {
            throw new IOException("File not found on disk");
        }

        long fileSize = Files.size(filePath);
        if (offset < 0 || offset >= fileSize) {
            throw new InvalidFileException("Invalid offset");
        }

        int bytesToRead = (int) Math.min(length, fileSize - offset);
        byte[] buffer = new byte[bytesToRead];

        try (java.io.RandomAccessFile file = new java.io.RandomAccessFile(filePath.toFile(), "r")) {
            file.seek(offset);
            file.readFully(buffer);
        }

        return buffer;
    }


    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("File is empty");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new InvalidFileException("File type not allowed. Supported: PDF, images, TXT, DOCX");
        }
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "file";
        }

        String name = Paths.get(filename).getFileName().toString();
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
