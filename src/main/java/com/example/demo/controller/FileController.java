package com.example.demo.controller;

import com.example.demo.DTOs.FileResponseDTO;
import com.example.demo.DTOs.InitUploadRequest;
import com.example.demo.DTOs.InitUploadResponse;
import com.example.demo.entities.FileMetadata;
import com.example.demo.security.CustomUserDetails;
import com.example.demo.service.FileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import com.example.demo.DTOs.DownloadInfoResponseDTO;

@RestController
@RequestMapping("/files")
@Slf4j
public class FileController {

    @Autowired
    private FileService fileService;



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

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) throws IOException {
        fileService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
