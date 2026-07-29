package com.femstep.store.service;

import com.femstep.store.domain.AuthSession;
import com.femstep.store.domain.Role;
import com.femstep.store.domain.RoleName;
import com.femstep.store.domain.User;
import com.femstep.store.dto.auth.AuthResponse;
import com.femstep.store.dto.auth.LoginRequest;
import com.femstep.store.dto.auth.RegisterRequest;
import com.femstep.store.dto.auth.UserResponse;
import com.femstep.store.dto.auth.UpdateProfileRequest;
import com.femstep.store.exception.ConflictException;
import com.femstep.store.exception.InvalidSessionException;
import com.femstep.store.exception.ResourceNotFoundException;
import com.femstep.store.repository.AuthSessionRepository;
import com.femstep.store.repository.RoleRepository;
import com.femstep.store.repository.UserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

@Service
@Transactional
public class AuthService {

    private static final int REFRESH_TOKEN_BYTES = 48;
    private static final int REFRESH_TOKEN_DAYS = 30;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuthSessionRepository authSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            AuthenticationManager authenticationManager,
            UserRepository userRepository,
            RoleRepository roleRepository,
            AuthSessionRepository authSessionRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.authSessionRepository = authSessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthResult login(LoginRequest request, ClientInfo clientInfo) {
        String email = normalizeEmail(request.getEmail());
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.getPassword())
        );

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
        user.setLastLoginAt(LocalDateTime.now());

        return createAuthResult(user, userDetails, clientInfo);
    }

    public AuthResult register(RegisterRequest request, ClientInfo clientInfo) {
        String email = normalizeEmail(request.getEmail());
        String cpf = normalizeOptionalDigits(request.getCpf());

        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Já existe uma conta com este e-mail");
        }
        if (cpf != null && userRepository.existsByCpf(cpf)) {
            throw new ConflictException("Já existe uma conta com este CPF");
        }

        Role customerRole = roleRepository.findByName(RoleName.ROLE_CUSTOMER)
                .orElseThrow(() -> new IllegalStateException("Papel de cliente não configurado"));

        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFirstName(request.getFirstName().trim());
        user.setLastName(request.getLastName().trim());
        user.setCpf(cpf);
        user.setActive(true);
        user.addRole(customerRole);
        user = userRepository.save(user);

        UserDetails userDetails = toUserDetails(user);
        return createAuthResult(user, userDetails, clientInfo);
    }

    public AuthResult refresh(String rawRefreshToken, ClientInfo clientInfo) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidSessionException("Sessão não encontrada");
        }

        AuthSession currentSession = authSessionRepository
                .findByTokenHashAndRevokedAtIsNull(hash(rawRefreshToken))
                .orElseThrow(() -> new InvalidSessionException("Sessão inválida"));

        if (!currentSession.isUsable()) {
            currentSession.setRevokedAt(LocalDateTime.now());
            throw new InvalidSessionException("Sessão expirada");
        }

        currentSession.setRevokedAt(LocalDateTime.now());
        User user = currentSession.getUser();
        return createAuthResult(user, toUserDetails(user), clientInfo);
    }

    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        authSessionRepository.findByTokenHashAndRevokedAtIsNull(hash(rawRefreshToken))
                .ifPresent(session -> session.setRevokedAt(LocalDateTime.now()));
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(String email) {
        User user = userRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateProfile(String email, UpdateProfileRequest request) {
        User user = userRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
        String cpf = normalizeOptionalDigits(request.cpf());
        if (cpf != null && userRepository.findByCpf(cpf)
                .filter(existing -> !existing.getId().equals(user.getId()))
                .isPresent()) {
            throw new ConflictException("CPF já está em uso");
        }

        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setCpf(cpf);
        return UserResponse.from(userRepository.save(user));
    }

    private AuthResult createAuthResult(User user, UserDetails userDetails, ClientInfo clientInfo) {
        String accessToken = jwtService.generateToken(userDetails);
        String refreshToken = generateRefreshToken();

        AuthSession session = new AuthSession();
        session.setUser(user);
        session.setTokenHash(hash(refreshToken));
        session.setUserAgent(truncate(clientInfo.userAgent(), 500));
        session.setIpAddress(truncate(clientInfo.ipAddress(), 64));
        session.setExpiresAt(LocalDateTime.now().plusDays(REFRESH_TOKEN_DAYS));
        authSessionRepository.save(session);

        AuthResponse response = new AuthResponse(
                accessToken,
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRoles().stream().map(role -> role.getName().name()).sorted().toList(),
                jwtService.getExpirationSeconds()
        );
        return new AuthResult(response, refreshToken);
    }

    private UserDetails toUserDetails(User user) {
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPassword())
                .authorities(user.getRoles().stream()
                        .map(role -> role.getName().name())
                        .toArray(String[]::new))
                .disabled(!user.getActive())
                .build();
    }

    private String generateRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 não está disponível", exception);
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeOptionalDigits(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.replaceAll("\\D", "");
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public record ClientInfo(String userAgent, String ipAddress) {
    }

    public record AuthResult(AuthResponse response, String refreshToken) {
    }
}
