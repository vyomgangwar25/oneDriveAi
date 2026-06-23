package com.example.demo.DTOs;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;
@Builder
@Data
@AllArgsConstructor
public class FileResponseDTO {

    private UUID fileId;
    private String originalFileName;
    private String contentType;
    private long sizeBytes;
    private LocalDateTime uploadedAt;
}
