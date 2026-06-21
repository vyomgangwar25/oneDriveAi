package com.example.demo.service;
import com.example.demo.DTOs.LoginRequestDTO;
import com.example.demo.DTOs.LoginResponseDTO;
import com.example.demo.enums.Role;
import com.example.demo.exception.InvalidCredentialsException;
import com.example.demo.exception.UserAlreadyExistsException;
import com.example.demo.DTOs.SignupRequestDTO;
import com.example.demo.entities.User;
import com.example.demo.interfaces.AuthService;
import com.example.demo.repositories.UserRepository;
import com.example.demo.response.AuthResponse;
import com.example.demo.security.JwtService;
import com.example.demo.security.RefreshTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Override
    public AuthResponse signup(SignupRequestDTO signupRequest) {
        log.info("Signup request received for email={}", signupRequest.getEmail());

        // 1. Check if email already exists
        boolean exists = userRepository .findByEmail(signupRequest.getEmail()) .isPresent();
        if (exists)
        {
            log.warn("Signup failed. Email already exists={}", signupRequest.getEmail());
            throw new UserAlreadyExistsException("Email already registered");
        }
        // 2. Create User entity
        User user = new User();

        user.setEmail(signupRequest.getEmail());
        user.setRole(Role.USER);

        // 3. Encrypt password using BCrypt
        user.setPassword( passwordEncoder.encode(signupRequest.getPassword()) );

        // 4. Save user
        User newUser = userRepository.save(user);
        log.info("User registered successfully. userId={} email={}", newUser.getId(), newUser.getEmail()); // 5. Return response return new AuthResponse( "Signup successful", HttpStatus.CREATED.value() );
        return new AuthResponse("Signup successful");
    }

    @Override
    public LoginResponseDTO login(LoginRequestDTO loginRequest) {

        log.info("Login request received for email={}", loginRequest.getEmail());

        // 1. Find user
        User user = userRepository.findByEmail(loginRequest.getEmail())
                .orElseThrow(() -> {

                    log.warn("Login failed. Email not found={}", loginRequest.getEmail());

                    return new InvalidCredentialsException("Invalid email or password");
                });

        // 2. Match password
        boolean passwordMatches = passwordEncoder.matches(
                        loginRequest.getPassword(), user.getPassword());

        if (!passwordMatches) {

            log.warn("Login failed. Invalid password for email={}", loginRequest.getEmail());

            throw new InvalidCredentialsException(
                    "Invalid email or password"
            );
        }
        String token = jwtService.generateToken(user);
        String refreshToken=refreshTokenService.createRefreshToken(user.getId());
        log.info("Login successful. userId={} email={}", user.getId(), user.getEmail());

        return new LoginResponseDTO("Login successful",token,refreshToken);
    }

}
