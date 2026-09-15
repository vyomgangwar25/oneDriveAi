package com.example.demo.DTOs;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A ready to use download link.
 *
 * The whole URL is returned rather than the raw token, so callers never build
 * it themselves. That keeps the signing scheme on this side, and means moving
 * the files to object storage later only changes what this URL points at.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DownloadTokenResponseDTO {

    private String downloadUrl;

    /** How long the link stays usable, in seconds. */
    private long expiresIn;
}
