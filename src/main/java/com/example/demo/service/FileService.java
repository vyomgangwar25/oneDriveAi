package com.example.demo.service;

import com.example.demo.DTOs.FileResponseDTO;
import com.example.demo.DTOs.InitUploadRequest;
import com.example.demo.DTOs.InitUploadResponse;
import com.example.demo.entities.FileMetadata;
import com.example.demo.entities.UploadSession;
import com.example.demo.entities.User;
import com.example.demo.enums.UploadStatus;
import com.example.demo.exception.FileNotFoundException;
import com.example.demo.exception.InvalidFileException;
import com.example.demo.repositories.FileRepository;
import com.example.demo.repositories.UploadSessionRepository;
import com.example.demo.repositories.UserRepository;
import com.example.demo.security.CustomUserDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import com.example.demo.DTOs.DownloadInfoResponseDTO;
import com.example.demo.exception.InvalidFileException;
import org.springframework.http.MediaType;
@Slf4j
@Service
public class FileService {

    @Autowired
    private FileRepository fileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UploadSessionRepository uploadSessionRepository;

    @Autowired
    private LocalFileStorageService localFileStorageService;

@Autowired
private UploadSessionRepository repository;

        public InitUploadResponse createSession(Long userId, InitUploadRequest request) {

            UploadSession session = new UploadSession();

            session.setUploadId(UUID.randomUUID().toString());
            session.setUserId(userId);
            session.setFileName(request.getFileName());
            session.setTotalChunks(request.getTotalChunks());
            session.setUploadedChunks(0);
            session.setStatus(UploadStatus.IN_PROGRESS);

            repository.save(session);

            return new InitUploadResponse(
                    session.getUploadId()
            );
        }

    public void uploadChunk(String uploadId, Integer chunkNumber, MultipartFile chunk) throws IOException {

        UploadSession session = uploadSessionRepository.findById(uploadId).orElseThrow();

        Path chunkDir = Paths.get("uploads")
                        .resolve("temp")
                        .resolve(uploadId);

        Files.createDirectories(chunkDir);

        Path chunkPath =
                chunkDir.resolve(
                        chunkNumber + ".part"
                );

        chunk.transferTo(chunkPath);

        session.setUploadedChunks(session.getUploadedChunks() + 1);

        uploadSessionRepository.save(session);
    }

    public FileResponseDTO completeUpload(String uploadId) throws IOException {

        UploadSession session =
                uploadSessionRepository.findById(uploadId)
                        .orElseThrow();

        if (!session.getUploadedChunks()
                .equals(session.getTotalChunks())) {
            throw new RuntimeException("Chunks missing");
        }

        Path tempDir = Paths.get("uploads/temp/" + uploadId);

        Path finalDir = Paths.get("uploads/users/" + session.getUserId());
        Files.createDirectories(finalDir);

        Path finalFile = finalDir.resolve(session.getFileName());

        try (OutputStream out = Files.newOutputStream(finalFile)) {

            for (int i = 1; i <= session.getTotalChunks(); i++) {

                Path chunk = tempDir.resolve(i + ".part");

                Files.copy(chunk, out);
            }
        }

        // cleanup temp
        FileSystemUtils.deleteRecursively(tempDir);

        // save metadata
        FileMetadata metadata = new FileMetadata();
        metadata.setId(UUID.randomUUID());
        metadata.setUser(userRepository.getReferenceById(session.getUserId()));
        metadata.setOriginalFileName(session.getFileName());
        metadata.setStoragePath("users/" + session.getUserId() + "/" + session.getFileName());
//        metadata.setStoragePath(finalFile.toString());
        metadata.setSizeBytes(Files.size(finalFile));

        fileRepository.save(metadata);

        // update session
        session.setStatus(UploadStatus.COMPLETED);
        uploadSessionRepository.save(session);

            return FileResponseDTO.builder()
                    .fileId(metadata.getId())
                    .originalFileName(metadata.getOriginalFileName())
                    .contentType(metadata.getContentType())
                    .sizeBytes(metadata.getSizeBytes())
                    .uploadedAt(metadata.getCreatedAt())
                    .build();

    }
    public FileResponseDTO upload(MultipartFile file) throws IOException {
        CustomUserDetails currentUser = getCurrentUser();
        User user = userRepository.findById(currentUser.getUserId())
                .orElseThrow(() -> new FileNotFoundException("User not found"));

        UUID fileId = UUID.randomUUID();
        String storagePath = localFileStorageService.store(user.getId(), fileId, file);

        FileMetadata metadata = new FileMetadata();
        metadata.setId(fileId);
        metadata.setUser(user);
        metadata.setOriginalFileName(file.getOriginalFilename());
        metadata.setStoragePath(storagePath);
        metadata.setContentType(file.getContentType());
        metadata.setSizeBytes(file.getSize());

        FileMetadata saved = fileRepository.save(metadata);
        log.info("File uploaded. userId={} fileId={}", user.getId(), saved.getId());

        return toDto(saved);
    }


    public List<FileResponseDTO> listUserFiles() {
        Long userId = getCurrentUser().getUserId();
        return fileRepository.findByUser_IdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public Resource download(UUID fileId) throws IOException {
        FileMetadata metadata = getOwnedFile(fileId);
        return localFileStorageService.loadAsResource(metadata.getStoragePath());
    }

    public FileMetadata getOwnedFileMetadata(UUID fileId) {
        return getOwnedFile(fileId);
    }

    public void delete(UUID fileId) throws IOException {
        FileMetadata metadata = getOwnedFile(fileId);
        localFileStorageService.delete(metadata.getStoragePath());
        fileRepository.delete(metadata);
        log.info("File deleted. userId={} fileId={}", metadata.getUser().getId(), fileId);
    }

    public DownloadInfoResponseDTO getDownloadInfo(UUID fileId) {
        FileMetadata metadata = getOwnedFile(fileId);

        String contentType = metadata.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        return DownloadInfoResponseDTO.builder()
                .fileId(metadata.getId())
                .fileName(metadata.getOriginalFileName())
                .contentType(contentType)
                .sizeBytes(metadata.getSizeBytes())
                .build();
    }

    public byte[] downloadChunk(UUID fileId, int chunkNumber, int chunkSize) throws IOException {
        if (chunkNumber < 1) {
            throw new InvalidFileException("chunkNumber must be >= 1");
        }
        if (chunkSize < 1) {
            throw new InvalidFileException("chunkSize must be >= 1");
        }

        FileMetadata metadata = getOwnedFile(fileId);
        long fileSize = metadata.getSizeBytes();

        if (fileSize == 0) {
            throw new InvalidFileException("File is empty");
        }

        int totalChunks = (int) Math.ceil((double) fileSize / chunkSize);

        if (chunkNumber > totalChunks) {
            throw new InvalidFileException("chunkNumber exceeds total chunks: " + totalChunks);
        }

        long offset = (long) (chunkNumber - 1) * chunkSize;
        int length = (int) Math.min(chunkSize, fileSize - offset);

        return localFileStorageService.readChunk(metadata.getStoragePath(), offset, length);
    }

    public int getTotalDownloadChunks(UUID fileId, int chunkSize) {
        FileMetadata metadata = getOwnedFile(fileId);
        long fileSize = metadata.getSizeBytes();

        if (fileSize == 0) {
            return 0;
        }

        return (int) Math.ceil((double) fileSize / chunkSize);
    }

    private FileMetadata getOwnedFile(UUID fileId) {
        Long userId = getCurrentUser().getUserId();
        return fileRepository.findByIdAndUser_Id(fileId, userId)
                .orElseThrow(() -> new FileNotFoundException("File not found"));
    }

    private CustomUserDetails getCurrentUser() {
        return (CustomUserDetails) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
    }

    private FileResponseDTO toDto(FileMetadata metadata) {
        return new FileResponseDTO(
                metadata.getId(),
                metadata.getOriginalFileName(),
                metadata.getContentType(),
                metadata.getSizeBytes(),
                metadata.getCreatedAt()
        );
    }
}
