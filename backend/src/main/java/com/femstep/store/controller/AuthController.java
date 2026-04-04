package com.femstep.store.controller;

import com.femstep.store.dto.auth.AuthResponse;
import com.femstep.store.dto.auth.LoginRequest;
import com.femstep.store.dto.auth.RegisterRequest;
import com.femstep.store.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/me")
    public ResponseEntity<String> getCurrentUser() {
        // TODO: Implementar retorno do usuário atual
        return ResponseEntity.ok("Current user endpoint - TODO");
    }
}
