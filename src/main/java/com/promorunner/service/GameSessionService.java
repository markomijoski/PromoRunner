package com.promorunner.service;

import com.promorunner.dto.EndSessionResult;
import com.promorunner.dto.ScoreUpdateResult;
import com.promorunner.dto.StartSessionResult;
import com.promorunner.exception.NoPlaysRemainingException;
import com.promorunner.exception.SessionAlreadyCompletedException;
import com.promorunner.exception.SessionNotFoundException;
import com.promorunner.exception.UserNotFoundException;
import com.promorunner.model.GameSession;
import com.promorunner.model.User;
import com.promorunner.repository.GameSessionRepository;
import com.promorunner.repository.ScoreRepository;
import com.promorunner.repository.UserRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GameSessionService {

    private final UserRepository userRepository;
    private final GameSessionRepository gameSessionRepository;
    private final ScoreRepository scoreRepository;
    private final ScoreService scoreService;
    private final LeaderboardService leaderboardService;

    public GameSessionService(
            UserRepository userRepository,
            GameSessionRepository gameSessionRepository,
            ScoreRepository scoreRepository,
            ScoreService scoreService,
            LeaderboardService leaderboardService) {
        this.userRepository = userRepository;
        this.gameSessionRepository = gameSessionRepository;
        this.scoreRepository = scoreRepository;
        this.scoreService = scoreService;
        this.leaderboardService = leaderboardService;
    }

    @Transactional
    public StartSessionResult startSession(long userId, String deviceType) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        Integer remaining = user.getFreePlaysRemaining();
        if (remaining != null && remaining == 0) {
            throw new NoPlaysRemainingException();
        }
        if (remaining != null) {
            user.setFreePlaysRemaining(remaining - 1);
            userRepository.save(user);
            remaining = user.getFreePlaysRemaining();
        }

        GameSession session = new GameSession();
        session.setUser(user);
        session.setDeviceType(deviceType);
        gameSessionRepository.save(session);

        int playsRemaining = remaining == null ? -1 : remaining;
        return new StartSessionResult(session.getId(), true, playsRemaining);
    }

    @Transactional
    public EndSessionResult endSession(long sessionId, long userId, int score, int durationMs) {
        GameSession session = gameSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));

        if (session.isCompleted()) {
            throw new SessionAlreadyCompletedException(sessionId);
        }

        session.setEndedAt(Instant.now());
        session.setFinalScore(score);
        session.setDurationMs(durationMs);
        session.setCompleted(true);
        gameSessionRepository.save(session);

        ScoreUpdateResult update = scoreService.updateScore(userId, sessionId, score);
        int rank = leaderboardService.getRank(userId);
        long totalPlayers = scoreRepository.count();

        return new EndSessionResult(update.score(), update.personalBest(), rank, totalPlayers);
    }
}
