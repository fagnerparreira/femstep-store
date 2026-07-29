package com.femstep.store.dto.auth;

import com.femstep.store.domain.User;

import java.util.List;

public record UserResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        String cpf,
        boolean emailVerified,
        List<String> roles
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getCpf(),
                user.getEmailVerifiedAt() != null,
                user.getRoles().stream().map(role -> role.getName().name()).sorted().toList()
        );
    }
}
