package com.example.demo.repositories;

import com.example.demo.entities.FileMetadata;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface FileRepository extends JpaRepository<FileMetadata, UUID> {

    /**
     * One page of a user's files.
     *
     * Ordering is left to the Pageable rather than baked into the method name,
     * because paging needs a tie breaker as well as a sort column.
     */
    Page<FileMetadata> findByUser_Id(Long userId, Pageable pageable);

    /** Same, limited to files uploaded after a point in time. */
    Page<FileMetadata> findByUser_IdAndCreatedAtAfter(Long userId, LocalDateTime since, Pageable pageable);

    Optional<FileMetadata> findByIdAndUser_Id(UUID id, Long userId);
}
