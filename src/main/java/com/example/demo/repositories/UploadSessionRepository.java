package com.example.demo.repositories;

import com.example.demo.entities.UploadSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface UploadSessionRepository extends JpaRepository<UploadSession, String> {

    /**
     * Moves a session to ASSEMBLING, but only from IN_PROGRESS.
     *
     * Returns 1 to the single caller that won the claim and 0 to everyone
     * else. Reading the status and then writing it would leave a window for
     * two requests to both decide they may assemble, so the decision is left
     * to this one conditional statement.
     */
    @Modifying
    @Transactional
    @Query("""
            update UploadSession s
               set s.status = com.example.demo.enums.UploadStatus.ASSEMBLING
             where s.uploadId = :uploadId
               and s.status = com.example.demo.enums.UploadStatus.IN_PROGRESS
            """)
    int claimForAssembly(@Param("uploadId") String uploadId);
}
