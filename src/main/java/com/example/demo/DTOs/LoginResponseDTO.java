package com.example.demo.DTOs;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponseDTO {
    private String message;
    private String accessToken;

    /**
     * Carried from the service to the controller only. The controller writes it
     * into the HttpOnly refresh cookie, and it is never serialized into the
     * response body, so a script on the page can never read it.
     */
    @JsonIgnore
    private String refreshToken;

    private String username;
    private String email;
}
