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
import java.util.Locale;
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

        Path target = resolveStoredFile(userId, fileId, file.getOriginalFilename());

        file.transferTo(target);

        return toStoragePath(target);
    }

    /**
     * Resolves where a file is stored on disk.
     *
     * The fileId prefix is what keeps two uploads of the same name apart, so
     * neither overwrites the other and neither collides on the unique
     * storage_path column.
     */
    public Path resolveStoredFile(Long userId, UUID fileId, String fileName) throws IOException {
        return resolveUserFile(userId, fileId + "_" + sanitizeFilename(fileName));
    }

    /**
     * Resolves a client supplied file name inside the owning user's directory.
     *
     * The name is sanitized and the resolved path is verified to stay inside
     * that directory, so a name such as "../../../evil.txt" cannot escape it.
     */
    private Path resolveUserFile(Long userId, String fileName) throws IOException {
        Path userDir = basePath.resolve("users").resolve(userId.toString());
        Files.createDirectories(userDir);

        Path target = userDir.resolve(sanitizeFilename(fileName)).normalize();

        if (!target.startsWith(userDir)) {
            throw new InvalidFileException("Invalid file path");
        }

        return target;
    }

    public String toStoragePath(Path target) {
        return basePath.relativize(target).toString().replace("\\", "/");
    }

    /**
     * Directory holding the not yet assembled chunks of an upload session.
     *
     * The uploadId is server generated, so unlike a file name it needs no
     * sanitizing.
     */
    public Path tempChunkDir(String uploadId) {
        return basePath.resolve("temp").resolve(uploadId);
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


    /**
     * Checks a declared content type against the allow list and returns it in
     * its bare, comparable form.
     *
     * A client may legitimately send parameters such as
     * "text/plain; charset=UTF-8", which must not fail the allow list, and the
     * stored value should not carry them either.
     *
     * This is the client's claim about the bytes, not proof: it is not verified
     * against the file's actual content.
     */
    public String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            throw new InvalidFileException("contentType is required");
        }

        String normalized = contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);

        if (!ALLOWED_CONTENT_TYPES.contains(normalized)) {
            throw new InvalidFileException("File type not allowed. Supported: PDF, images, TXT, DOCX");
        }

        return normalized;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("File is empty");
        }

        normalizeContentType(file.getContentType());
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "file";
        }

        String name = Paths.get(filename).getFileName().toString();
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
