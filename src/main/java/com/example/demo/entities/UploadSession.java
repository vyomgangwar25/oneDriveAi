package com.example.demo.entities;

import com.example.demo.enums.UploadStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "upload_sessions")
@Getter
@Setter
@NoArgsConstructor
public class UploadSession {

    @Id
    private String uploadId;

    /**
     * Id the assembled file will be stored under.
     *
     * Reserved when the session is created so that completing the same
     * session twice resolves to one file instead of two.
     */
    private UUID fileId;

    private Long userId;

    private String fileName;

    /** Content type declared at init, validated against the allow list. */
    private String contentType;

    private Integer totalChunks;

    /** Full size of the file the client declared at init, in bytes. */
    private Long totalSize;

    /** Size of every chunk except the last one, in bytes. */
    private Integer chunkSize;

    @Enumerated(EnumType.STRING)
    private UploadStatus status;
}
