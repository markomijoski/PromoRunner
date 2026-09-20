package com.promorunner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.promorunner.dto.EndSessionResult;
import com.promorunner.dto.ScoreUpdateResult;
import com.promorunner.dto.StartSessionResult;
import com.promorunner.exception.NoPlaysRemainingException;
import com.promorunner.exception.SessionAlreadyCompletedException;
import com.promorunner.exception.SessionNotFoundException;
import com.promorunner.model.GameSession;
import com.promorunner.model.User;
import com.promorunner.repository.GameSessionRepository;
import com.promorunner.repository.ScoreRepository;
import com.promorunner.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GameSessionServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private GameSessionRepository gameSessionRepository;
    @Mock
    private ScoreRepository scoreRepository;
    @Mock
    private ScoreService scoreService;
    @Mock
    private LeaderboardService leaderboardService;

    @InjectMocks
    private GameSessionService gameSessionService;

    @Test
    void startSessionThrowsWhenNoPlaysRemaining() {
        User user = user(1L, 0);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> gameSessionService.startSession(1L, "desktop"))
                .isInstanceOf(NoPlaysRemainingException.class);
    }

    @Test
    void startSessionDecrementsFreePlays() {
        User user = user(1L, 3);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(gameSessionRepository.save(any(GameSession.class))).thenAnswer(inv -> {
            GameSession session = inv.getArgument(0);
            session.setId(42L);
            return session;
        });

        StartSessionResult result = gameSessionService.startSession(1L, "mobile");

        assertThat(result.sessionId()).isEqualTo(42L);
        assertThat(result.canPlay()).isTrue();
        assertThat(result.playsRemaining()).isEqualTo(2);
        assertThat(user.getFreePlaysRemaining()).isEqualTo(2);
        verify(userRepository).save(user);
    }

    @Test
    void startSessionUnlimitedWhenPlaysNull() {
        User user = user(1L, null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(gameSessionRepository.save(any(GameSession.class))).thenAnswer(inv -> {
            GameSession session = inv.getArgument(0);
            session.setId(7L);
            return session;
        });

        StartSessionResult result = gameSessionService.startSession(1L, "desktop");

        assertThat(result.playsRemaining()).isEqualTo(-1);
        assertThat(user.getFreePlaysRemaining()).isNull();
    }

    @Test
    void endSessionHappyPath() {
        User user = user(1L, null);
        GameSession session = new GameSession();
        session.setId(42L);
        session.setUser(user);
        session.setCompleted(false);

        when(gameSessionRepository.findByIdAndUserId(42L, 1L)).thenReturn(Optional.of(session));
        when(scoreService.updateScore(1L, 42L, 4820))
                .thenReturn(new ScoreUpdateResult(4820, 4820, true, 1));
        when(leaderboardService.getRank(1L)).thenReturn(3);
        when(scoreRepository.count()).thenReturn(124L);

        EndSessionResult result = gameSessionService.endSession(42L, 1L, 4820, 94200);

        assertThat(result).isEqualTo(new EndSessionResult(4820, true, 3, 124L));
        assertThat(session.isCompleted()).isTrue();
        assertThat(session.getFinalScore()).isEqualTo(4820);
        assertThat(session.getDurationMs()).isEqualTo(94200);
        verify(scoreService).updateScore(eq(1L), eq(42L), eq(4820));
    }

    @Test
    void endSessionThrowsWhenNotFound() {
        when(gameSessionRepository.findByIdAndUserId(9L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gameSessionService.endSession(9L, 1L, 10, 100))
                .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    void endSessionThrowsWhenAlreadyCompleted() {
        GameSession session = new GameSession();
        session.setId(42L);
        session.setCompleted(true);
        when(gameSessionRepository.findByIdAndUserId(42L, 1L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> gameSessionService.endSession(42L, 1L, 10, 100))
                .isInstanceOf(SessionAlreadyCompletedException.class);
    }

    private static User user(long id, Integer plays) {
        User user = new User();
        user.setId(id);
        user.setEmail("u@ex.com");
        user.setFreePlaysRemaining(plays);
        return user;
    }
}
