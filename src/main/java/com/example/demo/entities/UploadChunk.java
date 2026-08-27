package com.example.demo.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One received chunk of an upload session.
 *
 * The upload progress is the set of these rows, not a counter, so a retried
 * chunk cannot be counted twice and two chunks arriving at the same time
 * cannot overwrite each other's progress.
 */
@Entity
@Table(
        name = "upload_chunks",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_upload_chunks_upload_id_chunk_number",
                columnNames = {"upload_id", "chunk_number"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class UploadChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "upload_id", nullable = false)
    private String uploadId;

    @Column(name = "chunk_number", nullable = false)
    private Integer chunkNumber;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;
}
