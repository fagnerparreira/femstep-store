package com.femstep.store.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("timestamp", LocalDateTime.now());
        response.put("application", "FemStep Store Backend");
        response.put("version", "1.0.0");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/public/test")
    public ResponseEntity<Map<String, String>> publicTest() {
        Map<String, String> response = new HashMap<>();
        response.put("message", "This is a public endpoint");
        response.put("access", "unauthorized");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/protected/test")
    public ResponseEntity<Map<String, String>> protectedTest() {
        Map<String, String> response = new HashMap<>();
        response.put("message", "This is a protected endpoint");
        response.put("access", "authenticated");
        return ResponseEntity.ok(response);
    }
}
