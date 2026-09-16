package com.example.demo.DTOs;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequestDTO {

    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email address")
    private String email;

    // no length rule here on purpose: an old account may hold a shorter
    // password than signup allows today, and login must not lock it out
    @NotBlank(message = "Password is required")
    private String password;
}
