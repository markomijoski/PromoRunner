package com.promorunner.service;

import com.promorunner.dto.AccountExportDto;
import com.promorunner.exception.UserNotFoundException;
import com.promorunner.model.GameSession;
import com.promorunner.model.Score;
import com.promorunner.model.User;
import com.promorunner.repository.GameSessionRepository;
import com.promorunner.repository.PasswordResetTokenRepository;
import com.promorunner.repository.ScoreRepository;
import com.promorunner.repository.UserRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final UserRepository userRepository;
    private final ScoreRepository scoreRepository;
    private final GameSessionRepository gameSessionRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    public AccountService(
            UserRepository userRepository,
            ScoreRepository scoreRepository,
            GameSessionRepository gameSessionRepository,
            PasswordResetTokenRepository passwordResetTokenRepository) {
        this.userRepository = userRepository;
        this.scoreRepository = scoreRepository;
        this.gameSessionRepository = gameSessionRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
    }

    @Transactional(readOnly = true)
    public User requireUser(long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    @Transactional(readOnly = true)
    public AccountExportDto export(long userId) {
        User user = requireUser(userId);
        Score score = scoreRepository.findByUserId(userId).orElse(null);
        List<GameSession> sessions = gameSessionRepository.findByUserIdOrderByStartedAtDesc(userId);

        AccountExportDto.ScoreExport scoreExport = score == null
                ? null
                : new AccountExportDto.ScoreExport(
                        score.getBestScore(),
                        score.getTotalPlays(),
                        score.getLastPlayedAt()
                );

        List<AccountExportDto.SessionExport> sessionExports = sessions.stream()
                .map(s -> new AccountExportDto.SessionExport(
                        s.getId(),
                        s.getStartedAt(),
                        s.getEndedAt(),
                        s.getFinalScore(),
                        s.getDurationMs(),
                        s.getDeviceType(),
                        s.isCompleted()
                ))
                .toList();

        return new AccountExportDto(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.isEmailVerified(),
                user.isMarketingConsent(),
                user.isLeaderboardVisible(),
                user.getFreePlaysRemaining(),
                user.getCreatedAt(),
                user.getLastSeenAt(),
                scoreExport,
                sessionExports
        );
    }

    @Transactional
    public void setLeaderboardVisible(long userId, boolean visible) {
        User user = requireUser(userId);
        user.setLeaderboardVisible(visible);
        userRepository.save(user);
    }

    @Transactional
    public void deleteAccount(long userId) {
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException(userId);
        }
        passwordResetTokenRepository.deleteByUserId(userId);
        scoreRepository.deleteByUserId(userId);
        gameSessionRepository.deleteByUserId(userId);
        userRepository.deleteById(userId);
    }
}
