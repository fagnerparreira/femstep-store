package com.femstep.store.dto.auth;

import java.util.List;

public class AuthResponse {
    
    private String token;
    private String type = "Bearer";
    private Long userId;
    private String email;
    private String firstName;
    private String lastName;
    private List<String> roles;

    public AuthResponse() {}

    public AuthResponse(
            String token,
            Long userId,
            String email,
            String firstName,
            String lastName,
            List<String> roles) {
        this.token = token;
        this.userId = userId;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.roles = roles;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }
}
