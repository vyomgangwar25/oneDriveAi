package com.example.demo.interfaces;

import com.example.demo.DTOs.LoginRequestDTO;
import com.example.demo.DTOs.LoginResponseDTO;
import com.example.demo.DTOs.SignupRequestDTO;
import com.example.demo.response.AuthResponse;

public interface AuthService {
    AuthResponse signup(SignupRequestDTO signupRequest);

    LoginResponseDTO login(LoginRequestDTO loginRequest);
}
