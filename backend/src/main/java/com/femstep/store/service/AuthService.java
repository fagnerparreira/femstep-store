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
import com.femstep.store.exception.BusinessRuleException;
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
import org.springframework.jdbc.core.JdbcTemplate;
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
    private static final int ACCOUNT_TOKEN_BYTES = 32;
    private static final int PASSWORD_RESET_MINUTES = 30;
    private static final int EMAIL_VERIFICATION_HOURS = 24;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuthSessionRepository authSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JdbcTemplate jdbcTemplate;
    private final NotificationOutboxService notificationOutboxService;

    public AuthService(
            AuthenticationManager authenticationManager,
            UserRepository userRepository,
            RoleRepository roleRepository,
            AuthSessionRepository authSessionRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            JdbcTemplate jdbcTemplate,
            NotificationOutboxService notificationOutboxService
    ) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.authSessionRepository = authSessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jdbcTemplate = jdbcTemplate;
        this.notificationOutboxService = notificationOutboxService;
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
        issueEmailVerification(user);

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

    public void requestPasswordReset(String requestedEmail) {
        String email = normalizeEmail(requestedEmail);
        userRepository.findByEmail(email)
                .filter(User::getActive)
                .ifPresent(this::issuePasswordReset);
    }

    public void resetPassword(String rawToken, String newPassword) {
        AccountToken token = findUsableToken(
                "password_reset_tokens",
                rawToken,
                "Token de redefinição inválido ou expirado"
        );
        User user = userRepository.findById(token.userId())
                .filter(User::getActive)
                .orElseThrow(() -> new BusinessRuleException(
                        "Token de redefinição inválido ou expirado"
                ));
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                "UPDATE password_reset_tokens SET used_at = ? WHERE user_id = ? AND used_at IS NULL",
                now,
                user.getId()
        );
        jdbcTemplate.update(
                "UPDATE auth_sessions SET revoked_at = ?, updated_at = ? WHERE user_id = ? AND revoked_at IS NULL",
                now,
                now,
                user.getId()
        );
        notificationOutboxService.redactAccountTokenEvent(
                "PASSWORD_RESET_REQUESTED",
                user.getId(),
                token.id()
        );
    }

    public void verifyEmail(String rawToken) {
        AccountToken token = findUsableToken(
                "email_verification_tokens",
                rawToken,
                "Token de verificação inválido ou expirado"
        );
        LocalDateTime now = LocalDateTime.now();
        int updated = jdbcTemplate.update("""
                UPDATE users
                SET email_verified_at = COALESCE(email_verified_at, ?),
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND active = true AND deleted_at IS NULL
                """,
                now,
                token.userId()
        );
        if (updated != 1) {
            throw new BusinessRuleException("Token de verificação inválido ou expirado");
        }
        jdbcTemplate.update(
                "UPDATE email_verification_tokens SET used_at = ? WHERE user_id = ? AND used_at IS NULL",
                now,
                token.userId()
        );
        notificationOutboxService.redactAccountTokenEvent(
                "EMAIL_VERIFICATION_REQUESTED",
                token.userId(),
                token.id()
        );
    }

    public void resendEmailVerification(String email) {
        userRepository.findByEmail(normalizeEmail(email))
                .filter(user -> user.getActive()
                        && user.getEmailVerifiedAt() == null)
                .ifPresent(this::issueEmailVerification);
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

    private void issuePasswordReset(User user) {
        jdbcTemplate.update(
                "UPDATE password_reset_tokens SET used_at = CURRENT_TIMESTAMP WHERE user_id = ? AND used_at IS NULL",
                user.getId()
        );
        String rawToken = generateAccountToken();
        Long tokenId = jdbcTemplate.queryForObject("""
                INSERT INTO password_reset_tokens (user_id, token_hash, expires_at)
                VALUES (?, ?, ?)
                RETURNING id
                """,
                Long.class,
                user.getId(),
                hash(rawToken),
                LocalDateTime.now().plusMinutes(PASSWORD_RESET_MINUTES)
        );
        notificationOutboxService.enqueueAccountTokenEvent(
                "PASSWORD_RESET_REQUESTED",
                user.getId(),
                tokenId,
                user.getEmail(),
                rawToken
        );
    }

    private void issueEmailVerification(User user) {
        if (user.getEmailVerifiedAt() != null) {
            return;
        }
        jdbcTemplate.update(
                "UPDATE email_verification_tokens SET used_at = CURRENT_TIMESTAMP WHERE user_id = ? AND used_at IS NULL",
                user.getId()
        );
        String rawToken = generateAccountToken();
        Long tokenId = jdbcTemplate.queryForObject("""
                INSERT INTO email_verification_tokens (user_id, token_hash, expires_at)
                VALUES (?, ?, ?)
                RETURNING id
                """,
                Long.class,
                user.getId(),
                hash(rawToken),
                LocalDateTime.now().plusHours(EMAIL_VERIFICATION_HOURS)
        );
        notificationOutboxService.enqueueAccountTokenEvent(
                "EMAIL_VERIFICATION_REQUESTED",
                user.getId(),
                tokenId,
                user.getEmail(),
                rawToken
        );
    }

    private AccountToken findUsableToken(String table, String rawToken, String errorMessage) {
        if (!table.equals("password_reset_tokens")
                && !table.equals("email_verification_tokens")) {
            throw new IllegalArgumentException("Tabela de token não suportada");
        }
        return jdbcTemplate.query("""
                        SELECT id, user_id
                        FROM %s
                        WHERE token_hash = ? AND used_at IS NULL
                          AND expires_at > CURRENT_TIMESTAMP
                        FOR UPDATE
                        """.formatted(table),
                (rs, rowNum) -> new AccountToken(
                        rs.getLong("id"),
                        rs.getLong("user_id")
                ),
                hash(rawToken)
        ).stream().findFirst()
                .orElseThrow(() -> new BusinessRuleException(errorMessage));
    }

    private String generateAccountToken() {
        byte[] bytes = new byte[ACCOUNT_TOKEN_BYTES];
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

    private record AccountToken(Long id, Long userId) {
    }
}
