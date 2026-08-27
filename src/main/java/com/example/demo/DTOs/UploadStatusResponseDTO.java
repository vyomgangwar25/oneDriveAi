package com.example.demo.DTOs;

import com.example.demo.enums.UploadStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Progress of an upload session, so an interrupted client can resume instead
 * of starting the whole file again.
 */
@Builder
@Data
@AllArgsConstructor
public class UploadStatusResponseDTO {

    private String uploadId;
    private String fileName;
    private UploadStatus status;

    private Integer totalChunks;
    private Integer chunkSize;
    private Long totalSize;

    private long receivedChunks;

    /** Chunk numbers the client still has to send, empty once complete. */
    private List<Integer> missingChunks;
}
