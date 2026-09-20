package com.promorunner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.promorunner.config.AppProperties;
import com.promorunner.exception.InvalidMagicLinkException;
import com.promorunner.model.MagicLinkToken;
import com.promorunner.model.User;
import com.promorunner.repository.MagicLinkTokenRepository;
import com.promorunner.repository.MarketingConsentRepository;
import com.promorunner.repository.UserRepository;
import com.promorunner.security.UserPrincipal;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.context.SecurityContextHolder;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private MagicLinkTokenRepository magicLinkTokenRepository;
    @Mock
    private MarketingConsentRepository marketingConsentRepository;
    @Mock
    private JavaMailSender mailSender;
    @Mock
    private TemplateEngine templateEngine;

    private AppProperties appProperties;
    private MagicLinkRateLimiter rateLimiter;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        appProperties.setBaseUrl("http://localhost:8080");
        rateLimiter = new MagicLinkRateLimiter();
        authService = new AuthService(
                userRepository,
                magicLinkTokenRepository,
                marketingConsentRepository,
                mailSender,
                templateEngine,
                appProperties,
                rateLimiter,
                "noreply@localhost"
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requestMagicLinkCreatesUserInvalidatesOldTokensAndSendsEmail() {
        when(userRepository.findByEmailIgnoreCase("player@example.com")).thenReturn(Optional.empty());
        User saved = new User();
        saved.setId(1L);
        saved.setEmail("player@example.com");
        when(userRepository.save(any(User.class))).thenReturn(saved);
        when(magicLinkTokenRepository.invalidateUnusedForUser(1L)).thenReturn(0);
        when(templateEngine.process(eq("email/magic-link"), any(Context.class))).thenReturn("<html/>");
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        authService.requestMagicLink("  Player@Example.com ");

        ArgumentCaptor<MagicLinkToken> tokenCaptor = ArgumentCaptor.forClass(MagicLinkToken.class);
        verify(magicLinkTokenRepository).invalidateUnusedForUser(1L);
        verify(magicLinkTokenRepository).save(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().getTokenHash()).hasSize(64);
        assertThat(tokenCaptor.getValue().getExpiresAt()).isAfter(Instant.now());
        verify(mailSender).send(mimeMessage);
    }

    @Test
    void requestMagicLinkReusesExistingUser() {
        User existing = new User();
        existing.setId(7L);
        existing.setEmail("player@example.com");
        when(userRepository.findByEmailIgnoreCase("player@example.com")).thenReturn(Optional.of(existing));
        when(magicLinkTokenRepository.invalidateUnusedForUser(7L)).thenReturn(2);
        when(templateEngine.process(eq("email/magic-link"), any(Context.class))).thenReturn("<html/>");
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        authService.requestMagicLink("player@example.com");

        verify(userRepository, never()).save(any(User.class));
        verify(magicLinkTokenRepository).invalidateUnusedForUser(7L);
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void verifyTokenHappyPathRedirectsToConsentWhenNoConsentRecord() {
        User user = new User();
        user.setId(3L);
        user.setEmail("a@b.com");
        MagicLinkToken token = validToken(user);

        String raw = "raw-token-value";
        when(magicLinkTokenRepository.findByTokenHash(AuthService.sha256Hex(raw)))
                .thenReturn(Optional.of(token));
        when(marketingConsentRepository.existsByUserId(3L)).thenReturn(false);

        String redirect = authService.verifyToken(raw);

        assertThat(redirect).isEqualTo("/consent");
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(token.getUsedAt()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isInstanceOf(UserPrincipal.class);
        verify(userRepository).save(user);
    }

    @Test
    void verifyTokenRedirectsToGameWhenConsentExists() {
        User user = new User();
        user.setId(3L);
        user.setEmail("a@b.com");
        MagicLinkToken token = validToken(user);

        String raw = "another-token";
        when(magicLinkTokenRepository.findByTokenHash(AuthService.sha256Hex(raw)))
                .thenReturn(Optional.of(token));
        when(marketingConsentRepository.existsByUserId(3L)).thenReturn(true);

        assertThat(authService.verifyToken(raw)).isEqualTo("/game");
    }

    @Test
    void verifyTokenRejectsUsedToken() {
        User user = new User();
        user.setId(1L);
        MagicLinkToken token = validToken(user);
        token.setUsedAt(Instant.now());

        String raw = "used";
        when(magicLinkTokenRepository.findByTokenHash(AuthService.sha256Hex(raw)))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.verifyToken(raw))
                .isInstanceOf(InvalidMagicLinkException.class)
                .hasMessageContaining("already been used");
    }

    @Test
    void verifyTokenRejectsExpiredToken() {
        User user = new User();
        user.setId(1L);
        MagicLinkToken token = validToken(user);
        token.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));

        String raw = "expired";
        when(magicLinkTokenRepository.findByTokenHash(AuthService.sha256Hex(raw)))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.verifyToken(raw))
                .isInstanceOf(InvalidMagicLinkException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void verifyTokenRejectsUnknownToken() {
        when(magicLinkTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verifyToken("missing"))
                .isInstanceOf(InvalidMagicLinkException.class);
    }

    private static MagicLinkToken validToken(User user) {
        MagicLinkToken token = new MagicLinkToken();
        token.setUser(user);
        token.setTokenHash("hash");
        token.setExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
        return token;
    }
}
