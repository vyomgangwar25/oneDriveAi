package com.example.demo.entities;

import com.example.demo.enums.UploadStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "upload_sessions")
@Getter
@Setter
@NoArgsConstructor
public class UploadSession {

    @Id
    private String uploadId;

    private Long userId;

    private String fileName;

    private Integer totalChunks;

    private Integer uploadedChunks;

    @Enumerated(EnumType.STRING)
    private UploadStatus status;
}
