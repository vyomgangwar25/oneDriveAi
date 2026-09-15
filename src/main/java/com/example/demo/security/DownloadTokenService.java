package com.example.demo.security;

import com.example.demo.exception.InvalidCredentialsException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * Issues and checks the short lived tokens that ride in a download URL.
 *
 * A browser download is a plain navigation, and a navigation cannot carry an
 * Authorization header, so the link has to authorize itself. This is the same
 * shape as an S3 presigned URL: signed, bound to one file and one user, and
 * valid only long enough for the transfer to start.
 */
@Service
public class DownloadTokenService {

    /** Separates these from access tokens, which are signed with the same key. */
    private static final String TOKEN_TYPE = "download";

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.download-expiration}")
    private long downloadExpiration;

    private SecretKey getSigningKey() {
        // same derivation as JwtService, so both read the one configured secret
        byte[] keyBytes = Decoders.BASE64.decode(
                Base64.getEncoder().encodeToString(secretKey.getBytes()));

        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String createToken(UUID fileId, Long userId) {

        return Jwts.builder()
                .subject(fileId.toString())
                .claim("uid", userId)
                .claim("typ", TOKEN_TYPE)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + downloadExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    public long getExpirySeconds() {
        return downloadExpiration / 1000;
    }

    /**
     * Checks a token against the file it is being used for and returns the user
     * it was issued to.
     *
     * The file id is verified here rather than by the caller, so a token minted
     * for one file cannot be replayed against another.
     */
    public Long validateAndGetUserId(String token, UUID fileId) {

        if (token == null || token.isBlank()) {
            throw new InvalidCredentialsException("Missing download token");
        }

        Claims claims;

        try {
            claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException invalid) {
            // expired, tampered with, or not a token at all
            throw new InvalidCredentialsException("Invalid or expired download token");
        }

        if (!TOKEN_TYPE.equals(claims.get("typ", String.class))) {
            throw new InvalidCredentialsException("Invalid or expired download token");
        }

        if (!fileId.toString().equals(claims.getSubject())) {
            throw new InvalidCredentialsException("Invalid or expired download token");
        }

        Long userId = claims.get("uid", Long.class);

        if (userId == null) {
            throw new InvalidCredentialsException("Invalid or expired download token");
        }

        return userId;
    }
}
