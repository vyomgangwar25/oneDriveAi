package com.example.demo.repositories;

import com.example.demo.entities.FileMetadata;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FileRepository extends JpaRepository<FileMetadata, UUID> {

    List<FileMetadata> findByUser_IdOrderByCreatedAtDesc(Long userId);

    Optional<FileMetadata> findByIdAndUser_Id(UUID id, Long userId);
}
