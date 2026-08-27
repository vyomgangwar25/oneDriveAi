package com.example.demo.enums;

public enum UploadStatus {

    /** Accepting chunks. */
    IN_PROGRESS,

    /**
     * Claimed by one request that is merging the chunks.
     *
     * Entering this state is what stops a second /complete from assembling the
     * same session at the same time, and stops further chunks from replacing a
     * file the merge is reading.
     */
    ASSEMBLING,

    COMPLETED,
    FAILED
}
