package com.promorunner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.promorunner.config.AppProperties;
import com.promorunner.exception.InvalidResetTokenException;
import com.promorunner.model.PasswordResetToken;
import com.promorunner.model.User;
import com.promorunner.repository.PasswordResetTokenRepository;
import com.promorunner.repository.UserRepository;
import com.promorunner.security.UserPrincipal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private MailService mailService;

    private PasswordEncoder passwordEncoder;
    private AppProperties appProperties;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        appProperties = new AppProperties();
        appProperties.setBaseUrl("http://localhost:8080");
        authService = new AuthService(
                userRepository,
                passwordResetTokenRepository,
                passwordEncoder,
                authenticationManager,
                mailService,
                appProperties
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void registerCreatesUserHashesPasswordAndAuthenticates() {
        when(userRepository.existsByEmailIgnoreCase("player@example.com")).thenReturn(false);
        when(userRepository.existsByDisplayNameIgnoreCaseExcludingId(eq("player_one"), isNull()))
                .thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });
        Authentication auth = new UsernamePasswordAuthenticationToken(
                new UserPrincipal(1L, "player@example.com", "hash", com.promorunner.model.UserRole.PLAYER),
                null,
                java.util.List.of()
        );
        when(authenticationManager.authenticate(any())).thenReturn(auth);

        String redirect = authService.register("  Player@Example.com ", "player_one", "secret123");

        assertThat(redirect).isEqualTo("/game");
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, org.mockito.Mockito.atLeastOnce()).save(userCaptor.capture());
        User saved = userCaptor.getAllValues().get(0);
        assertThat(saved.getEmail()).isEqualTo("player@example.com");
        assertThat(saved.getDisplayName()).isEqualTo("player_one");
        assertThat(saved.getPasswordHash()).isNotBlank();
        assertThat(passwordEncoder.matches("secret123", saved.getPasswordHash())).isTrue();
        assertThat(saved.isEmailVerified()).isTrue();
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    void registerRejectsShortPassword() {
        when(userRepository.existsByDisplayNameIgnoreCaseExcludingId(eq("player"), isNull()))
                .thenReturn(false);

        assertThatThrownBy(() -> authService.register("a@b.com", "player", "short"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Лозинката");
    }

    @Test
    void loginSuccessUpdatesLastSeen() {
        User user = new User();
        user.setId(3L);
        user.setEmail("a@b.com");
        user.setDisplayName("alice");
        user.setPasswordHash(passwordEncoder.encode("secret123"));
        when(authenticationManager.authenticate(any())).thenReturn(
                new UsernamePasswordAuthenticationToken(
                        UserPrincipal.from(user), null, UserPrincipal.from(user).getAuthorities())
        );
        when(userRepository.findByEmailIgnoreCase("a@b.com")).thenReturn(Optional.of(user));

        assertThat(authService.login("a@b.com", "secret123")).isEqualTo("/game");
        assertThat(user.getLastSeenAt()).isNotNull();
        verify(userRepository).save(user);
    }

    @Test
    void loginFailsWithGenericMessage() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("bad"));

        assertThatThrownBy(() -> authService.login("missing@x.com", "wrongpass"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Погрешна е-пошта или лозинка");
    }

    @Test
    void requestPasswordResetSendsEmailWhenUserExists() {
        User user = new User();
        user.setId(7L);
        user.setEmail("player@example.com");
        when(userRepository.findByEmailIgnoreCase("player@example.com")).thenReturn(Optional.of(user));
        when(passwordResetTokenRepository.invalidateUnusedForUser(7L)).thenReturn(0);
        when(mailService.render(eq("email/password-reset"), any(Map.class))).thenReturn("<html/>");

        authService.requestPasswordReset("  Player@Example.com ");

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).invalidateUnusedForUser(7L);
        verify(passwordResetTokenRepository).save(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().getTokenHash()).hasSize(64);
        assertThat(tokenCaptor.getValue().getExpiresAt()).isAfter(Instant.now().plus(25, ChronoUnit.MINUTES));
        verify(mailService).sendHtml(eq("player@example.com"), anyString(), eq("<html/>"));
    }

    @Test
    void requestPasswordResetSilentWhenUserMissing() {
        when(userRepository.findByEmailIgnoreCase("nobody@x.com")).thenReturn(Optional.empty());

        authService.requestPasswordReset("nobody@x.com");

        verify(passwordResetTokenRepository, never()).save(any());
        verify(mailService, never()).sendHtml(anyString(), anyString(), anyString());
    }

    @Test
    void resetPasswordSetsHashForLegacyUser() {
        User user = new User();
        user.setId(9L);
        user.setEmail("legacy@x.com");
        user.setPasswordHash(null);
        PasswordResetToken token = validToken(user);
        String raw = "raw-token-value";
        when(passwordResetTokenRepository.findByTokenHash(AuthService.sha256Hex(raw)))
                .thenReturn(Optional.of(token));

        authService.resetPassword(raw, "newpass12");

        assertThat(token.getUsedAt()).isNotNull();
        assertThat(user.getPasswordHash()).isNotBlank();
        assertThat(passwordEncoder.matches("newpass12", user.getPasswordHash())).isTrue();
        assertThat(user.isEmailVerified()).isTrue();
        verify(userRepository).save(user);
    }

    @Test
    void resetPasswordRejectsUsedToken() {
        User user = new User();
        user.setId(1L);
        PasswordResetToken token = validToken(user);
        token.setUsedAt(Instant.now());
        String raw = "used";
        when(passwordResetTokenRepository.findByTokenHash(AuthService.sha256Hex(raw)))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.resetPassword(raw, "newpass12"))
                .isInstanceOf(InvalidResetTokenException.class)
                .hasMessageContaining("already been used");
    }

    @Test
    void resetPasswordRejectsExpiredToken() {
        User user = new User();
        user.setId(1L);
        PasswordResetToken token = validToken(user);
        token.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        String raw = "expired";
        when(passwordResetTokenRepository.findByTokenHash(AuthService.sha256Hex(raw)))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.resetPassword(raw, "newpass12"))
                .isInstanceOf(InvalidResetTokenException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void resetPasswordRejectsUnknownToken() {
        when(passwordResetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.resetPassword("missing", "newpass12"))
                .isInstanceOf(InvalidResetTokenException.class);
    }

    private static PasswordResetToken validToken(User user) {
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash("hash");
        token.setExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
        return token;
    }
}
