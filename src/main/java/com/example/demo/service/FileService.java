package com.example.demo.service;

import com.example.demo.DTOs.FileResponseDTO;
import com.example.demo.DTOs.InitUploadRequest;
import com.example.demo.DTOs.InitUploadResponse;
import com.example.demo.DTOs.UploadStatusResponseDTO;
import com.example.demo.entities.FileMetadata;
import com.example.demo.entities.UploadSession;
import com.example.demo.entities.User;
import com.example.demo.enums.UploadStatus;
import com.example.demo.exception.FileNotFoundException;
import com.example.demo.exception.InvalidFileException;
import com.example.demo.exception.UploadConflictException;
import com.example.demo.repositories.FileRepository;
import com.example.demo.repositories.UploadChunkRepository;
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
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
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
    private UploadChunkRepository uploadChunkRepository;

    public InitUploadResponse createSession(Long userId, InitUploadRequest request) {

        validateInitRequest(request);

        UploadSession session = new UploadSession();

        session.setUploadId(UUID.randomUUID().toString());
        session.setFileId(UUID.randomUUID());
        session.setUserId(userId);
        session.setFileName(request.getFileName());
        session.setContentType(
                localFileStorageService.normalizeContentType(request.getContentType()));
        session.setTotalChunks(request.getTotalChunks());
        session.setTotalSize(request.getTotalSize());
        session.setChunkSize(request.getChunkSize());
        session.setStatus(UploadStatus.IN_PROGRESS);

        uploadSessionRepository.save(session);

        return new InitUploadResponse(
                session.getUploadId()
        );
    }

    public void uploadChunk(String uploadId, Integer chunkNumber, MultipartFile chunk) throws IOException {

        UploadSession session = getOwnedSession(uploadId);
        requireSizedSession(session);

        if (session.getStatus() != UploadStatus.IN_PROGRESS) {
            throw new InvalidFileException(
                    "Upload session is not accepting chunks: " + session.getStatus());
        }

        if (chunkNumber == null || chunkNumber < 1 || chunkNumber > session.getTotalChunks()) {
            throw new InvalidFileException(
                    "chunkNumber must be between 1 and " + session.getTotalChunks());
        }

        // a chunk of the wrong size means a truncated or mis-sliced transfer,
        // and is the only chance to notice it while the client can still retry
        long expectedSize = expectedChunkSize(session, chunkNumber);
        if (chunk.getSize() != expectedSize) {
            throw new InvalidFileException(
                    "chunk " + chunkNumber + " must be " + expectedSize
                            + " bytes but was " + chunk.getSize());
        }

        Path chunkDir = localFileStorageService.tempChunkDir(uploadId);
        Files.createDirectories(chunkDir);

        Path chunkPath = chunkDir.resolve(chunkNumber + ".part");

        // the scratch name is unique per request, not per chunk: two requests
        // for the same chunk would otherwise write over each other's transfer
        // and one of them would publish the other's half written bytes
        Path scratchPath = chunkDir.resolve(chunkNumber + ".part." + UUID.randomUUID());

        // transfer into a scratch name and swap it in, so a transfer that dies
        // half way cannot leave a truncated .part behind for assembly to use
        try {
            chunk.transferTo(scratchPath);

            Files.move(scratchPath, chunkPath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(scratchPath);
        }

        int recorded = uploadChunkRepository.record(uploadId, chunkNumber, expectedSize);

        log.debug("Chunk stored. uploadId={} chunkNumber={} newRecord={}",
                uploadId, chunkNumber, recorded == 1);
    }

    public FileResponseDTO completeUpload(String uploadId) throws IOException {

        UploadSession session = getOwnedSession(uploadId);
        requireSizedSession(session);

        // a client retrying after a timed out /complete must get the same file
        // back, not a second copy of it
        if (session.getStatus() == UploadStatus.COMPLETED) {
            return fileRepository.findById(session.getFileId())
                    .map(this::toDto)
                    .orElseThrow(() -> new FileNotFoundException("Completed file not found"));
        }

        if (session.getStatus() == UploadStatus.ASSEMBLING) {
            throw new UploadConflictException(
                    "Upload is already being assembled, poll the status endpoint");
        }

        if (session.getStatus() != UploadStatus.IN_PROGRESS) {
            throw new InvalidFileException(
                    "Upload session cannot be completed: " + session.getStatus());
        }

        // chunk numbers are validated on the way in and unique per session, so
        // a full count means the set is exactly 1..totalChunks
        long received = uploadChunkRepository.countByUploadId(uploadId);
        if (received != session.getTotalChunks()) {
            throw new InvalidFileException(
                    "Upload incomplete: " + received + " of "
                            + session.getTotalChunks() + " chunks received");
        }

        // the status read above is only a fast path with a clear message; this
        // claim is the actual gate, because two requests can both pass the read
        if (uploadSessionRepository.claimForAssembly(uploadId) != 1) {
            throw new UploadConflictException(
                    "Upload is already being assembled, poll the status endpoint");
        }
        session.setStatus(UploadStatus.ASSEMBLING);

        Path tempDir = localFileStorageService.tempChunkDir(uploadId);

        // fileName comes from the client, so resolve it through the storage
        // service which sanitizes it, prefixes the fileId so uploads of the
        // same name cannot collide, and keeps it inside the user's directory
        Path finalFile = localFileStorageService.resolveStoredFile(
                session.getUserId(),
                session.getFileId(),
                session.getFileName()
        );

        // unique per attempt, so a scratch file left behind by a killed attempt
        // is never truncated and republished by the next one
        Path scratchFile = finalFile.resolveSibling(
                finalFile.getFileName() + "." + UUID.randomUUID() + ".tmp");

        try {
            assembleChunks(session, tempDir, scratchFile);

            long assembledSize = Files.size(scratchFile);
            if (assembledSize != session.getTotalSize()) {
                throw new InvalidFileException(
                        "Assembled file is " + assembledSize + " bytes but "
                                + session.getTotalSize() + " was declared");
            }

            // only now does the verified content take the real file name
            Files.move(scratchFile, finalFile,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);

        } catch (IOException | RuntimeException failure) {
            Files.deleteIfExists(scratchFile);

            session.setStatus(UploadStatus.FAILED);
            uploadSessionRepository.save(session);

            log.warn("Upload assembly failed. uploadId={} reason={}",
                    uploadId, failure.getMessage());
            throw failure;
        }

        FileMetadata metadata = new FileMetadata();
        metadata.setId(session.getFileId());
        metadata.setUser(userRepository.getReferenceById(session.getUserId()));
        metadata.setOriginalFileName(session.getFileName());
        metadata.setStoragePath(localFileStorageService.toStoragePath(finalFile));
        metadata.setContentType(session.getContentType());
        metadata.setSizeBytes(session.getTotalSize());

        FileMetadata saved = fileRepository.save(metadata);

        session.setStatus(UploadStatus.COMPLETED);
        uploadSessionRepository.save(session);

        // drop the chunks only once the file and its metadata are durable, so a
        // crash before this point leaves a session that can still be completed
        FileSystemUtils.deleteRecursively(tempDir);
        uploadChunkRepository.deleteByUploadId(uploadId);

        log.info("Chunked upload completed. userId={} fileId={} sizeBytes={}",
                session.getUserId(), saved.getId(), saved.getSizeBytes());

        return toDto(saved);
    }

    /**
     * Reports which chunks of a session are still outstanding.
     *
     * This is what lets an interrupted client resume: it sends only the
     * missing chunks instead of the whole file again.
     */
    public UploadStatusResponseDTO getUploadStatus(String uploadId) {

        UploadSession session = getOwnedSession(uploadId);
        requireSizedSession(session);

        List<Integer> missingChunks;
        long receivedChunks;

        if (session.getStatus() == UploadStatus.COMPLETED) {
            // the chunk records are dropped on completion, nothing is missing
            missingChunks = List.of();
            receivedChunks = session.getTotalChunks();
        } else {
            Set<Integer> received =
                    new HashSet<>(uploadChunkRepository.findChunkNumbers(uploadId));

            missingChunks = IntStream.rangeClosed(1, session.getTotalChunks())
                    .filter(chunkNumber -> !received.contains(chunkNumber))
                    .boxed()
                    .toList();

            receivedChunks = received.size();
        }

        return UploadStatusResponseDTO.builder()
                .uploadId(session.getUploadId())
                .fileName(session.getFileName())
                .status(session.getStatus())
                .totalChunks(session.getTotalChunks())
                .chunkSize(session.getChunkSize())
                .totalSize(session.getTotalSize())
                .receivedChunks(receivedChunks)
                .missingChunks(missingChunks)
                .build();
    }

    /**
     * Concatenates the stored chunks in order into {@code target}.
     *
     * Every chunk is checked against the size the session declares for it, so
     * a chunk that went missing or was damaged on disk fails the upload here
     * instead of producing a silently corrupt file.
     */
    private void assembleChunks(UploadSession session, Path tempDir, Path target) throws IOException {

        try (OutputStream out = Files.newOutputStream(target,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)) {

            for (int chunkNumber = 1; chunkNumber <= session.getTotalChunks(); chunkNumber++) {

                Path chunk = tempDir.resolve(chunkNumber + ".part");

                if (!Files.exists(chunk)) {
                    throw new InvalidFileException(
                            "Chunk " + chunkNumber + " is missing from disk");
                }

                long expectedSize = expectedChunkSize(session, chunkNumber);
                long actualSize = Files.size(chunk);

                if (actualSize != expectedSize) {
                    throw new InvalidFileException(
                            "Chunk " + chunkNumber + " is " + actualSize
                                    + " bytes on disk but should be " + expectedSize);
                }

                Files.copy(chunk, out);
            }
        }
    }

    /** Size chunk {@code chunkNumber} must have, the last one being shorter. */
    private long expectedChunkSize(UploadSession session, int chunkNumber) {
        long offset = (long) (chunkNumber - 1) * session.getChunkSize();
        return Math.min(session.getChunkSize(), session.getTotalSize() - offset);
    }

    private void validateInitRequest(InitUploadRequest request) {

        if (request.getFileName() == null || request.getFileName().isBlank()) {
            throw new InvalidFileException("fileName is required");
        }
        if (request.getContentType() == null || request.getContentType().isBlank()) {
            throw new InvalidFileException("contentType is required");
        }
        if (request.getTotalSize() == null || request.getTotalSize() < 1) {
            throw new InvalidFileException("totalSize must be >= 1");
        }
        if (request.getChunkSize() == null || request.getChunkSize() < 1) {
            throw new InvalidFileException("chunkSize must be >= 1");
        }
        if (request.getTotalChunks() == null || request.getTotalChunks() < 1) {
            throw new InvalidFileException("totalChunks must be >= 1");
        }

        // ceil without floating point, which loses precision on large files
        long expectedChunks =
                (request.getTotalSize() + request.getChunkSize() - 1) / request.getChunkSize();

        if (expectedChunks != request.getTotalChunks()) {
            throw new InvalidFileException(
                    "totalChunks must be " + expectedChunks
                            + " for totalSize=" + request.getTotalSize()
                            + " and chunkSize=" + request.getChunkSize());
        }
    }

    /**
     * Rejects sessions created before size tracking existed, whose chunks
     * cannot be validated or assembled safely.
     */
    private void requireSizedSession(UploadSession session) {
        if (session.getFileId() == null
                || session.getTotalSize() == null
                || session.getChunkSize() == null
                || session.getTotalChunks() == null) {

            throw new InvalidFileException(
                    "Upload session is missing size information, please start a new upload");
        }
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
        metadata.setContentType(
                localFileStorageService.normalizeContentType(file.getContentType()));
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

    /**
     * Loads an upload session that belongs to the current user.
     *
     * A session owned by somebody else is reported as not found rather than
     * forbidden, so callers cannot probe for existing upload ids.
     */
    private UploadSession getOwnedSession(String uploadId) {
        Long userId = getCurrentUser().getUserId();

        UploadSession session = uploadSessionRepository.findById(uploadId)
                .orElseThrow(() -> new FileNotFoundException("Upload session not found"));

        if (!userId.equals(session.getUserId())) {
            log.warn("Upload session access denied. uploadId={} ownerId={} callerId={}",
                    uploadId, session.getUserId(), userId);
            throw new FileNotFoundException("Upload session not found");
        }

        return session;
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
