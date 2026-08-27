package com.example.demo.repositories;

import com.example.demo.entities.UploadChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface UploadChunkRepository extends JpaRepository<UploadChunk, Long> {

    /**
     * Records a received chunk, ignoring a chunk that was already recorded.
     *
     * A retry or two concurrent requests for the same chunk resolve against
     * the unique constraint inside a single statement, so there is no
     * read-modify-write window for progress to be lost or double counted.
     */
    @Modifying
    @Transactional
    @Query(
            value = """
                    INSERT INTO upload_chunks (upload_id, chunk_number, size_bytes)
                    VALUES (:uploadId, :chunkNumber, :sizeBytes)
                    ON CONFLICT (upload_id, chunk_number) DO NOTHING
                    """,
            nativeQuery = true
    )
    int record(
            @Param("uploadId") String uploadId,
            @Param("chunkNumber") Integer chunkNumber,
            @Param("sizeBytes") long sizeBytes
    );

    long countByUploadId(String uploadId);

    @Query("select c.chunkNumber from UploadChunk c where c.uploadId = :uploadId order by c.chunkNumber")
    List<Integer> findChunkNumbers(@Param("uploadId") String uploadId);

    @Modifying
    @Transactional
    void deleteByUploadId(String uploadId);
}
