package com.example.demo.exception;

/**
 * The upload session is busy with a conflicting operation.
 *
 * Distinct from InvalidFileException so the client is told to poll and retry
 * rather than that its request was malformed.
 */
public class UploadConflictException extends RuntimeException {

    public UploadConflictException(String message) {
        super(message);
    }
}
