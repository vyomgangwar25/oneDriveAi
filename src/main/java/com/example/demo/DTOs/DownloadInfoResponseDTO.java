package com.example.demo.DTOs;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DownloadInfoResponseDTO {
        private UUID fileId;
        private String fileName;
        private String contentType;
        private long sizeBytes;

}
