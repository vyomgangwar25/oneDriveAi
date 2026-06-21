package com.example.demo.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

@Service
public class CookieService {

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    public void addRefreshCookie(HttpServletResponse response, String token) {

        ResponseCookie cookie = ResponseCookie.from("refresh_token", token)
                        .httpOnly(true)
                        .secure(false) // true in prod HTTPS
                        .path("/")
                        .sameSite("Strict")
                        .maxAge(refreshExpiration / 1000)
                        .build();

        response.addHeader(
                "Set-Cookie",
                cookie.toString()
        );
    }

    public void clearRefreshCookie(
            HttpServletResponse response
    ) {

        ResponseCookie cookie =
                ResponseCookie.from(
                                "refresh_token",
                                ""
                        )
                        .httpOnly(true)
                        .secure(false)
                        .path("/")
                        .sameSite("Strict")
                        .maxAge(0)
                        .build();

        response.addHeader(
                "Set-Cookie",
                cookie.toString()
        );
    }
}