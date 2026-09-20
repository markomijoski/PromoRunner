package com.promorunner.service;

import com.promorunner.dto.ScoreUpdateResult;
import com.promorunner.event.ScoreSubmittedEvent;
import com.promorunner.exception.UserNotFoundException;
import com.promorunner.model.Score;
import com.promorunner.model.User;
import com.promorunner.repository.ScoreRepository;
import com.promorunner.repository.UserRepository;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScoreService {

    private final ScoreRepository scoreRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ScoreService(
            ScoreRepository scoreRepository,
            UserRepository userRepository,
            ApplicationEventPublisher eventPublisher) {
        this.scoreRepository = scoreRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ScoreUpdateResult updateScore(long userId, long sessionId, int score) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        Score entity = scoreRepository.findByUserId(userId).orElseGet(() -> {
            Score created = new Score();
            created.setUser(user);
            return created;
        });

        boolean personalBest = score > entity.getBestScore();
        if (personalBest) {
            entity.setBestScore(score);
        }
        entity.setTotalPlays(entity.getTotalPlays() + 1);
        entity.setLastPlayedAt(Instant.now());
        scoreRepository.save(entity);

        eventPublisher.publishEvent(new ScoreSubmittedEvent(userId, sessionId, score, personalBest));

        return new ScoreUpdateResult(score, entity.getBestScore(), personalBest, entity.getTotalPlays());
    }
}
