package com.promorunner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.promorunner.dto.AccountExportDto;
import com.promorunner.model.Score;
import com.promorunner.model.User;
import com.promorunner.repository.GameSessionRepository;
import com.promorunner.repository.PasswordResetTokenRepository;
import com.promorunner.repository.ScoreRepository;
import com.promorunner.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ScoreRepository scoreRepository;
    @Mock
    private GameSessionRepository gameSessionRepository;
    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(
                userRepository,
                scoreRepository,
                gameSessionRepository,
                passwordResetTokenRepository
        );
    }

    @Test
    void exportIncludesProfileAndScore() {
        User user = new User();
        user.setId(1L);
        user.setEmail("a@b.com");
        user.setDisplayName("alice");
        user.setMarketingConsent(true);
        user.setLeaderboardVisible(true);
        Score score = new Score();
        score.setBestScore(100);
        score.setTotalPlays(3);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(scoreRepository.findByUserId(1L)).thenReturn(Optional.of(score));
        when(gameSessionRepository.findByUserIdOrderByStartedAtDesc(1L)).thenReturn(List.of());

        AccountExportDto dto = accountService.export(1L);

        assertThat(dto.email()).isEqualTo("a@b.com");
        assertThat(dto.username()).isEqualTo("alice");
        assertThat(dto.score().bestScore()).isEqualTo(100);
        assertThat(dto.sessions()).isEmpty();
    }

    @Test
    void deleteAccountRemovesRelatedRows() {
        when(userRepository.existsById(9L)).thenReturn(true);

        accountService.deleteAccount(9L);

        verify(passwordResetTokenRepository).deleteByUserId(9L);
        verify(scoreRepository).deleteByUserId(9L);
        verify(gameSessionRepository).deleteByUserId(9L);
        verify(userRepository).deleteById(9L);
    }

    @Test
    void setLeaderboardVisibleUpdatesFlag() {
        User user = new User();
        user.setId(2L);
        user.setLeaderboardVisible(true);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));

        accountService.setLeaderboardVisible(2L, false);

        assertThat(user.isLeaderboardVisible()).isFalse();
        verify(userRepository).save(user);
    }
}
