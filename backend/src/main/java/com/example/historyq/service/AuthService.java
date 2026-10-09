package com.example.historyq.service;

import com.example.historyq.dto.AuthResponse;
import com.example.historyq.dto.CreateUserRequest;
import com.example.historyq.dto.LoginRequest;
import com.example.historyq.entity.User;
import com.example.historyq.entity.UserRole;
import com.example.historyq.entity.UserRoleId;
import com.example.historyq.repository.UserRepository;
import com.example.historyq.repository.UserRoleRepository;
import com.example.historyq.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;


import java.time.OffsetDateTime;

@Service
public class AuthService {

    private static final String ROLE = "USER";

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    public AuthResponse register(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username già esistente.");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email già esistente.");
        }

        User user = new User();
        user.setUsername(request.username().trim());
        user.setEmail(request.email().trim().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setEnabled(true);
        user.setCreatedAt(OffsetDateTime.now());
        User savedUser = userRepository.save(user);

        userRoleRepository.save(new UserRole(
                new UserRoleId(savedUser.getId(), ROLE),
                savedUser
        ));

        UserDetails details = org.springframework.security.core.userdetails.User
                .withUsername(savedUser.getUsername())
                .password(savedUser.getPasswordHash())
                .authorities(ROLE)
                .build();
        return new AuthResponse(jwtService.generateToken(details), savedUser.getUsername(), "USER");
    }

    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        } catch (AuthenticationException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Username o password non validi.");
        }

        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Username o password non validi."));
        UserDetails details = org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .authorities(ROLE)
                .disabled(!user.isEnabled())
                .build();
        return new AuthResponse(jwtService.generateToken(details), user.getUsername(), "USER");
    }
}
