package com.promorunner.service;

import com.promorunner.config.AppProperties;
import com.promorunner.exception.InvalidResetTokenException;
import com.promorunner.model.PasswordResetToken;
import com.promorunner.model.User;
import com.promorunner.repository.PasswordResetTokenRepository;
import com.promorunner.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final int RESET_TOKEN_TTL_MINUTES = 30;
    private static final int PASSWORD_MIN = 8;
    private static final int USERNAME_MIN = 3;
    private static final int USERNAME_MAX = 20;
    private static final Pattern USERNAME_PATTERN = Pattern.compile("[\\p{L}\\p{N}_]+");
    private static final String GENERIC_LOGIN_ERROR = "Погрешна е-пошта или лозинка";

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final MailService mailService;
    private final AppProperties appProperties;

    public AuthService(
            UserRepository userRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            MailService mailService,
            AppProperties appProperties) {
        this.userRepository = userRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.mailService = mailService;
        this.appProperties = appProperties;
    }

    @Transactional
    public String register(String email, String nickname, String password) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail.isBlank() || !normalizedEmail.contains("@")) {
            throw new IllegalArgumentException("Потребна е валидна е-пошта");
        }
        String normalizedUsername = normalizeUsername(nickname);
        if (normalizedUsername.isBlank()) {
            throw new IllegalArgumentException("Корисничкото име е задолжително");
        }
        validateUsername(normalizedUsername, null);
        validatePassword(password);

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new IllegalArgumentException("Е-поштата е веќе регистрирана");
        }

        User user = new User();
        user.setEmail(normalizedEmail);
        user.setDisplayName(normalizedUsername);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setEmailVerified(true);
        userRepository.save(user);

        authenticate(normalizedEmail, password);
        user.setLastSeenAt(Instant.now());
        userRepository.save(user);
        return "/game";
    }

    @Transactional
    public String login(String email, String password) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail.isBlank() || password == null || password.isBlank()) {
            throw new IllegalArgumentException(GENERIC_LOGIN_ERROR);
        }

        try {
            authenticate(normalizedEmail, password);
        } catch (BadCredentialsException ex) {
            throw new IllegalArgumentException(GENERIC_LOGIN_ERROR);
        }

        User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException(GENERIC_LOGIN_ERROR));
        user.setLastSeenAt(Instant.now());
        userRepository.save(user);

        if (user.getDisplayName() == null || user.getDisplayName().isBlank()) {
            return "/game?modal=username";
        }
        return "/game";
    }

    @Transactional
    public void requestPasswordReset(String email) {
        String normalized = normalizeEmail(email);
        if (normalized.isBlank()) {
            return;
        }

        userRepository.findByEmailIgnoreCase(normalized).ifPresent(user -> {
            passwordResetTokenRepository.invalidateUnusedForUser(user.getId());

            String rawToken = UUID.randomUUID().toString();
            PasswordResetToken token = new PasswordResetToken();
            token.setUser(user);
            token.setTokenHash(sha256Hex(rawToken));
            token.setExpiresAt(Instant.now().plus(RESET_TOKEN_TTL_MINUTES, ChronoUnit.MINUTES));
            passwordResetTokenRepository.save(token);

            sendPasswordResetEmail(user.getEmail(), rawToken);
        });
    }

    @Transactional(readOnly = true)
    public void requireValidResetToken(String rawToken) {
        findValidResetToken(rawToken);
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        validatePassword(newPassword);
        PasswordResetToken token = findValidResetToken(rawToken);

        token.setUsedAt(Instant.now());
        passwordResetTokenRepository.save(token);

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setEmailVerified(true);
        userRepository.save(user);
    }

    @Transactional
    public void setUsername(long userId, String username) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        String normalizedUsername = normalizeUsername(username);
        if (normalizedUsername.isBlank()) {
            throw new IllegalArgumentException("Корисничкото име е задолжително");
        }
        validateUsername(normalizedUsername, userId);
        user.setDisplayName(normalizedUsername);
        userRepository.save(user);
    }

    private void authenticate(String email, String password) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, password)
        );
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }

    private PasswordResetToken findValidResetToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidResetTokenException("Reset token is missing");
        }
        String hash = sha256Hex(rawToken.trim());
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidResetTokenException("Invalid reset link"));

        if (token.getUsedAt() != null) {
            throw new InvalidResetTokenException("Reset link has already been used");
        }
        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidResetTokenException("Reset link has expired");
        }
        return token;
    }

    void validateUsername(String username, Long excludeUserId) {
        if (username.length() < USERNAME_MIN || username.length() > USERNAME_MAX) {
            throw new IllegalArgumentException(
                    "Корисничкото име мора да има меѓу " + USERNAME_MIN + " и " + USERNAME_MAX + " знаци");
        }
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new IllegalArgumentException(
                    "Корисничкото име може да содржи само букви, бројки и _");
        }
        if (userRepository.existsByDisplayNameIgnoreCaseExcludingId(username, excludeUserId)) {
            throw new IllegalArgumentException("Тоа корисничко име е зафатено");
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < PASSWORD_MIN) {
            throw new IllegalArgumentException(
                    "Лозинката мора да има најмалку " + PASSWORD_MIN + " знаци");
        }
    }

    private void sendPasswordResetEmail(String to, String rawToken) {
        String resetUrl = appProperties.getBaseUrl().replaceAll("/$", "")
                + "/auth/reset?token=" + rawToken;

        String html = mailService.render("email/password-reset", Map.of(
                "resetUrl", resetUrl,
                "expiresMinutes", RESET_TOKEN_TTL_MINUTES
        ));
        mailService.sendHtml(to, "Ресетирање на лозинка — AMSM Runner", html);
    }

    static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    static String normalizeUsername(String username) {
        return username == null ? "" : username.trim();
    }

    static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
