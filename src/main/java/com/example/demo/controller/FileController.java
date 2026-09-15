package com.example.demo.controller;

import com.example.demo.DTOs.FileResponseDTO;
import com.example.demo.DTOs.InitUploadRequest;
import com.example.demo.DTOs.InitUploadResponse;
import com.example.demo.DTOs.UploadStatusResponseDTO;
import com.example.demo.entities.FileMetadata;
import com.example.demo.security.CustomUserDetails;
import com.example.demo.security.DownloadTokenService;
import com.example.demo.service.FileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import com.example.demo.DTOs.DownloadInfoResponseDTO;
import com.example.demo.DTOs.DownloadTokenResponseDTO;

@RestController
@RequestMapping("/files")
@Slf4j
public class FileController {

    @Autowired
    private FileService fileService;

    @Autowired
    private DownloadTokenService downloadTokenService;

    /** Host the download links point at, so they work outside this machine too. */
    @Value("${app.public-base-url}")
    private String publicBaseUrl;



    @PostMapping("/init")
    public ResponseEntity<InitUploadResponse> initUpload(@RequestBody InitUploadRequest request) {

        CustomUserDetails user =
                (CustomUserDetails)
                        SecurityContextHolder.getContext()
                                .getAuthentication()
                                .getPrincipal();

        InitUploadResponse response =
                fileService.createSession(
                        user.getUserId(),
                        request
                );

        return ResponseEntity.ok(response);
    }

    @PostMapping(
            value = "/{uploadId}/chunk",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<Void> uploadChunk(
            @PathVariable String uploadId,
            @RequestParam Integer chunkNumber,
            @RequestParam MultipartFile file
    ) throws IOException {

        fileService.uploadChunk(
                uploadId,
                chunkNumber,
                file
        );

        return ResponseEntity.ok().build();
    }

    @GetMapping("/{uploadId}/status")
    public ResponseEntity<UploadStatusResponseDTO> uploadStatus(
            @PathVariable String uploadId
    ) {
        return ResponseEntity.ok(fileService.getUploadStatus(uploadId));
    }

    @PostMapping("/{uploadId}/complete")
    public ResponseEntity<FileResponseDTO> completeUpload(
            @PathVariable String uploadId
    ) throws IOException {

        FileResponseDTO response =
                fileService.completeUpload(uploadId);

        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FileResponseDTO> upload(@RequestParam("file") MultipartFile file) throws IOException {
        FileResponseDTO response = fileService.upload(file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<FileResponseDTO>> listFiles() {
        return ResponseEntity.ok(fileService.listUserFiles());
    }
    @GetMapping("/{id}/download/info")
    public ResponseEntity<DownloadInfoResponseDTO> downloadInfo(@PathVariable UUID id) {
        return ResponseEntity.ok(fileService.getDownloadInfo(id));
    }

    @GetMapping("/{id}/download/chunk")
    public ResponseEntity<byte[]> downloadChunk(
            @PathVariable UUID id,
            @RequestParam Integer chunkNumber,
            @RequestParam Integer chunkSize
    ) throws IOException {
        FileMetadata metadata = fileService.getOwnedFileMetadata(id);
        byte[] chunk = fileService.downloadChunk(id, chunkNumber, chunkSize);
        int totalChunks = fileService.getTotalDownloadChunks(id, chunkSize);

        String contentType = metadata.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header("X-Chunk-Number", String.valueOf(chunkNumber))
                .header("X-Total-Chunks", String.valueOf(totalChunks))
                .header("X-File-Size", String.valueOf(metadata.getSizeBytes()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + metadata.getOriginalFileName() + "\"")
                .body(chunk);
    }

    /**
     * Mints a short lived link for the browser to download with.
     *
     * Ownership is checked here, while the caller still has a JWT, so the link
     * itself only has to prove that this check already happened.
     */
    @PostMapping("/{id}/download-token")
    public ResponseEntity<DownloadTokenResponseDTO> downloadToken(@PathVariable UUID id) {

        FileMetadata metadata = fileService.getOwnedFileMetadata(id);

        String token = downloadTokenService.createToken(
                metadata.getId(),
                metadata.getUser().getId()
        );

        String downloadUrl = UriComponentsBuilder
                .fromUriString(publicBaseUrl)
                .path("/files/{id}/download")
                .queryParam("token", token)
                .buildAndExpand(metadata.getId())
                .toUriString();

        return ResponseEntity.ok(
                DownloadTokenResponseDTO.builder()
                        .downloadUrl(downloadUrl)
                        .expiresIn(downloadTokenService.getExpirySeconds())
                        .build()
        );
    }

    /**
     * Streams a file to the browser.
     *
     * This one is reached by navigation rather than by fetch, so there is no
     * Authorization header to read and the token in the query string is the
     * authorization. A Range request is answered with 206 and only that slice,
     * which is what lets a browser resume an interrupted download.
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<?> download(
            @PathVariable UUID id,
            @RequestParam String token,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader
    ) throws IOException {

        Long userId = downloadTokenService.validateAndGetUserId(token, id);

        FileMetadata metadata = fileService.getFileForUser(id, userId);
        Resource resource = fileService.loadResource(metadata);

        long fileSize = metadata.getSizeBytes();

        String contentType = metadata.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        // built rather than concatenated, so a name carrying a quote cannot
        // break the header and a non ASCII name survives as filename*=UTF-8''
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(metadata.getOriginalFileName(), StandardCharsets.UTF_8)
                .build();

        if (rangeHeader == null) {
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .contentLength(fileSize)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .body(resource);
        }

        List<HttpRange> ranges = HttpRange.parseRanges(rangeHeader);
        HttpRange range = ranges.get(0);

        long start = range.getRangeStart(fileSize);
        long end = range.getRangeEnd(fileSize);

        ResourceRegion region = new ResourceRegion(resource, start, end - start + 1);

        // Content-Range and the length of the slice are filled in by Spring's
        // ResourceRegion converter
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(region);
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) throws IOException {
        fileService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
