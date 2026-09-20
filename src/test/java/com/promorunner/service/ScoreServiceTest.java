package com.promorunner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.promorunner.dto.ScoreUpdateResult;
import com.promorunner.event.ScoreSubmittedEvent;
import com.promorunner.model.Score;
import com.promorunner.model.User;
import com.promorunner.repository.ScoreRepository;
import com.promorunner.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ScoreServiceTest {

    @Mock
    private ScoreRepository scoreRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ScoreService scoreService;

    @Test
    void firstScoreCreatesRowAndIsPersonalBest() {
        User user = user(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(scoreRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(scoreRepository.save(any(Score.class))).thenAnswer(inv -> inv.getArgument(0));

        ScoreUpdateResult result = scoreService.updateScore(1L, 10L, 500);

        assertThat(result.personalBest()).isTrue();
        assertThat(result.bestScore()).isEqualTo(500);
        assertThat(result.totalPlays()).isEqualTo(1);
        verifyEvent(1L, 10L, 500, true);
    }

    @Test
    void newBestUpdatesBestScore() {
        User user = user(1L);
        Score existing = new Score();
        existing.setUser(user);
        existing.setBestScore(100);
        existing.setTotalPlays(2);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(scoreRepository.findByUserId(1L)).thenReturn(Optional.of(existing));
        when(scoreRepository.save(any(Score.class))).thenAnswer(inv -> inv.getArgument(0));

        ScoreUpdateResult result = scoreService.updateScore(1L, 11L, 250);

        assertThat(result.personalBest()).isTrue();
        assertThat(result.bestScore()).isEqualTo(250);
        assertThat(result.totalPlays()).isEqualTo(3);
    }

    @Test
    void nonBestStillIncrementsPlaysAndPublishesEvent() {
        User user = user(1L);
        Score existing = new Score();
        existing.setUser(user);
        existing.setBestScore(900);
        existing.setTotalPlays(5);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(scoreRepository.findByUserId(1L)).thenReturn(Optional.of(existing));
        when(scoreRepository.save(any(Score.class))).thenAnswer(inv -> inv.getArgument(0));

        ScoreUpdateResult result = scoreService.updateScore(1L, 12L, 100);

        assertThat(result.personalBest()).isFalse();
        assertThat(result.bestScore()).isEqualTo(900);
        assertThat(result.totalPlays()).isEqualTo(6);
        verifyEvent(1L, 12L, 100, false);
    }

    private void verifyEvent(long userId, long sessionId, int score, boolean personalBest) {
        ArgumentCaptor<ScoreSubmittedEvent> captor = ArgumentCaptor.forClass(ScoreSubmittedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new ScoreSubmittedEvent(userId, sessionId, score, personalBest));
    }

    private static User user(long id) {
        User user = new User();
        user.setId(id);
        user.setEmail("u@ex.com");
        return user;
    }
}
