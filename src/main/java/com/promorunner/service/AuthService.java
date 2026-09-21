package com.promorunner.service;

import com.promorunner.config.AppProperties;
import com.promorunner.exception.InvalidMagicLinkException;
import com.promorunner.model.MagicLinkToken;
import com.promorunner.model.User;
import com.promorunner.repository.MagicLinkTokenRepository;
import com.promorunner.repository.UserRepository;
import com.promorunner.security.UserPrincipal;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
public class AuthService {

    private static final int TOKEN_TTL_MINUTES = 15;

    private final UserRepository userRepository;
    private final MagicLinkTokenRepository magicLinkTokenRepository;
    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final AppProperties appProperties;
    private final MagicLinkRateLimiter rateLimiter;
    private final String mailFrom;

    public AuthService(
            UserRepository userRepository,
            MagicLinkTokenRepository magicLinkTokenRepository,
            JavaMailSender mailSender,
            TemplateEngine templateEngine,
            AppProperties appProperties,
            MagicLinkRateLimiter rateLimiter,
            @Value("${spring.mail.from:noreply@localhost}") String mailFrom) {
        this.userRepository = userRepository;
        this.magicLinkTokenRepository = magicLinkTokenRepository;
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.appProperties = appProperties;
        this.rateLimiter = rateLimiter;
        this.mailFrom = mailFrom;
    }

    @Transactional
    public void requestMagicLink(String email) {
        String normalized = normalizeEmail(email);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        rateLimiter.check(normalized);

        User user = userRepository.findByEmailIgnoreCase(normalized)
                .orElseGet(() -> createUser(normalized));

        magicLinkTokenRepository.invalidateUnusedForUser(user.getId());

        String rawToken = UUID.randomUUID().toString();
        MagicLinkToken token = new MagicLinkToken();
        token.setUser(user);
        token.setTokenHash(sha256Hex(rawToken));
        token.setExpiresAt(Instant.now().plus(TOKEN_TTL_MINUTES, ChronoUnit.MINUTES));
        magicLinkTokenRepository.save(token);

        sendMagicLinkEmail(user.getEmail(), rawToken);
    }

    @Transactional
    public String verifyToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidMagicLinkException("Magic link token is missing");
        }

        String hash = sha256Hex(rawToken.trim());
        MagicLinkToken token = magicLinkTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidMagicLinkException("Invalid magic link"));

        if (token.getUsedAt() != null) {
            throw new InvalidMagicLinkException("Magic link has already been used");
        }
        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidMagicLinkException("Magic link has expired");
        }

        token.setUsedAt(Instant.now());
        magicLinkTokenRepository.save(token);

        User user = token.getUser();
        user.setEmailVerified(true);
        user.setLastSeenAt(Instant.now());
        userRepository.save(user);

        UserPrincipal principal = UserPrincipal.from(user);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        return "/game";
    }

    private User createUser(String email) {
        User user = new User();
        user.setEmail(email);
        return userRepository.save(user);
    }

    private void sendMagicLinkEmail(String to, String rawToken) {
        String verifyUrl = appProperties.getBaseUrl().replaceAll("/$", "")
                + "/auth/verify?token=" + rawToken;

        Context context = new Context(Locale.ENGLISH);
        context.setVariables(Map.of(
                "verifyUrl", verifyUrl,
                "expiresMinutes", TOKEN_TTL_MINUTES
        ));
        String html = templateEngine.process("email/magic-link", context);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(mailFrom);
            helper.setTo(to);
            helper.setSubject("Вашиот AMSM Runner линк за најава");
            helper.setText(html, true);
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new IllegalStateException("Failed to send magic link email", e);
        }
    }

    static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
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
