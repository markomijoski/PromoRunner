package com.promorunner.service;

import com.promorunner.dto.LeaderboardEntry;
import com.promorunner.dto.LeaderboardSnapshot;
import com.promorunner.exception.UserNotFoundException;
import com.promorunner.model.Score;
import com.promorunner.model.User;
import com.promorunner.repository.ScoreRepository;
import com.promorunner.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeaderboardService {

    public static final int TOP_LIMIT = 25;

    private final ScoreRepository scoreRepository;
    private final UserRepository userRepository;

    public LeaderboardService(ScoreRepository scoreRepository, UserRepository userRepository) {
        this.scoreRepository = scoreRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public LeaderboardSnapshot getTop() {
        return getTop(TOP_LIMIT);
    }

    @Transactional(readOnly = true)
    public LeaderboardSnapshot getTop(int limit) {
        List<Score> scores = scoreRepository.findTopByOrderByBestScoreDesc(PageRequest.of(0, limit));
        long totalPlayers = scoreRepository.count();

        List<LeaderboardEntry> entries = new ArrayList<>(scores.size());
        int rank = 1;
        for (Score score : scores) {
            entries.add(new LeaderboardEntry(
                    rank++,
                    score.getUser().getId(),
                    displayName(score.getUser()),
                    score.getBestScore()
            ));
        }
        return new LeaderboardSnapshot(entries, totalPlayers);
    }

    /**
     * Top {@link #TOP_LIMIT} plus the viewer's own row appended when they have a score
     * outside that list.
     */
    @Transactional(readOnly = true)
    public LeaderboardSnapshot forViewer(Long userId) {
        LeaderboardSnapshot top = getTop();
        if (userId == null) {
            return top;
        }
        boolean inList = top.entries().stream().anyMatch(e -> e.userId() == userId);
        if (inList) {
            return top;
        }
        Optional<LeaderboardEntry> mine = getMyRank(userId);
        if (mine.isEmpty()) {
            return top;
        }
        List<LeaderboardEntry> entries = new ArrayList<>(top.entries());
        entries.add(mine.get());
        return new LeaderboardSnapshot(entries, top.totalPlayers());
    }

    @Transactional(readOnly = true)
    public Optional<LeaderboardEntry> getMyRank(long userId) {
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException(userId);
        }
        return scoreRepository.findByUserId(userId).map(score -> new LeaderboardEntry(
                getRank(userId),
                userId,
                displayName(score.getUser()),
                score.getBestScore()
        ));
    }

    @Transactional(readOnly = true)
    public int getRank(long userId) {
        Score score = scoreRepository.findByUserId(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        long better = scoreRepository.countByBestScoreGreaterThan(score.getBestScore());
        return (int) better + 1;
    }

    static String displayName(User user) {
        String email = user.getEmail();
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }
}
