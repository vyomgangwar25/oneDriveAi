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
    public ResponseEntity<AuthResponse> signup(@RequestBody SignupRequestDTO request) {

        AuthResponse response = authService.signup(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@RequestBody LoginRequestDTO request) {
        LoginResponseDTO response = authService.login(request);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
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
                new LoginResponseDTO("access_token",
                        accessToken,"token refreshed successfully"
                )
        );
    }


}
