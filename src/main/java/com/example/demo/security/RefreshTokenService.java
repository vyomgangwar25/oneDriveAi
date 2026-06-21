package com.example.demo.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RefreshTokenService {
    private final Map<String, TokenData> tokenStore = new ConcurrentHashMap<>();

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    public String createRefreshToken(Long userId) {

        String refreshToken = UUID.randomUUID().toString();

        long expiry = System.currentTimeMillis() + refreshExpiration;

        tokenStore.put(refreshToken, new TokenData(userId, expiry));

        return refreshToken;
    }

    public Long validateRefreshToken(String token) {

        TokenData data = tokenStore.get(token);

        if (data == null || data.getExpiry() < System.currentTimeMillis()) {

            tokenStore.remove(token);

            throw new RuntimeException("Invalid refresh token");
        }

        return data.getUserId();
    }

    public void revokeToken(String token) {
        tokenStore.remove(token);
    }

    public String rotateToken(String oldToken) {

        Long userId = validateRefreshToken(oldToken);

        revokeToken(oldToken);

        return createRefreshToken(userId);
    }


}
