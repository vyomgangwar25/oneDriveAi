package com.example.demo.controller;

import com.example.demo.DTOs.LoginRequestDTO;
import com.example.demo.DTOs.LoginResponseDTO;
import com.example.demo.DTOs.SignupRequestDTO;
import com.example.demo.entities.User;
import com.example.demo.repositories.UserRepository;
import com.example.demo.response.AuthResponse;
import com.example.demo.security.CookieService;
import com.example.demo.security.JwtService;
import com.example.demo.security.RefreshTokenService;
import com.example.demo.service.AuthServiceImpl;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@Slf4j
public class AuthController {

    @Autowired
    private AuthServiceImpl authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
private CookieService cookieService;

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequestDTO request) {

        AuthResponse response = authService.signup(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(
            @Valid @RequestBody LoginRequestDTO request,
            HttpServletResponse httpResponse) {

        LoginResponseDTO response = authService.login(request);

        // the refresh token leaves the server only as an HttpOnly cookie, so
        // that a reload can silently re-authenticate without the token ever
        // being reachable from JavaScript
        cookieService.addRefreshCookie(
                httpResponse,
                response.getRefreshToken()
        );

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<AuthResponse> logout(
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            HttpServletResponse response) {

        // revoking is what actually ends the session; clearing the cookie alone
        // would leave a still-valid token in the store
        if (refreshToken != null) {
            refreshTokenService.revokeToken(refreshToken);
        }

        cookieService.clearRefreshCookie(response);

        return ResponseEntity.ok(new AuthResponse("Logout successful"));
    }


    @PostMapping("/refresh")
    public ResponseEntity<LoginResponseDTO> refresh(@CookieValue(name = "refresh_token", required = false)
            String refreshToken, HttpServletResponse response) {

        String newRefreshToken = refreshTokenService.rotateToken(refreshToken);

        cookieService.addRefreshCookie(
                response,
                newRefreshToken
        );

        Long userId = refreshTokenService.validateRefreshToken(newRefreshToken);

        User user = userRepository.findById(userId).orElseThrow();

        String accessToken = jwtService.generateToken(user);

        return ResponseEntity.ok(
                LoginResponseDTO.builder()
                        .message("token refreshed successfully")
                        .accessToken(accessToken)
                        .username(user.getUsername())
                        .email(user.getEmail())
                        .build()
        );
    }


}
