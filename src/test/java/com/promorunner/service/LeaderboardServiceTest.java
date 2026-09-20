package com.promorunner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.promorunner.dto.LeaderboardEntry;
import com.promorunner.dto.LeaderboardSnapshot;
import com.promorunner.model.Score;
import com.promorunner.model.User;
import com.promorunner.repository.ScoreRepository;
import com.promorunner.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class LeaderboardServiceTest {

    @Mock
    private ScoreRepository scoreRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private LeaderboardService leaderboardService;

    @Test
    void getTopTenMapsRanksAndDisplayNames() {
        User jane = user(1L, "jane@ex.com", "Jane D.");
        User mark = user(2L, "mark@ex.com", null);
        Score s1 = score(jane, 12540);
        Score s2 = score(mark, 9820);

        when(scoreRepository.findTopByOrderByBestScoreDesc(any(Pageable.class)))
                .thenReturn(List.of(s1, s2));
        when(scoreRepository.count()).thenReturn(124L);

        LeaderboardSnapshot snapshot = leaderboardService.getTopTen();

        assertThat(snapshot.totalPlayers()).isEqualTo(124);
        assertThat(snapshot.entries()).containsExactly(
                new LeaderboardEntry(1, 1L, "Jane D.", 12540),
                new LeaderboardEntry(2, 2L, "mark", 9820)
        );
    }

    @Test
    void getRankCountsBetterScores() {
        User user = user(5L, "me@ex.com", "Me");
        Score mine = score(user, 400);
        when(scoreRepository.findByUserId(5L)).thenReturn(Optional.of(mine));
        when(scoreRepository.countByBestScoreGreaterThan(400)).thenReturn(2L);

        assertThat(leaderboardService.getRank(5L)).isEqualTo(3);
    }

    @Test
    void getMyRankReturnsEmptyWhenNoScoreRow() {
        when(userRepository.existsById(9L)).thenReturn(true);
        when(scoreRepository.findByUserId(9L)).thenReturn(Optional.empty());

        assertThat(leaderboardService.getMyRank(9L)).isEmpty();
    }

    private static User user(long id, String email, String displayName) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setDisplayName(displayName);
        return user;
    }

    private static Score score(User user, int best) {
        Score score = new Score();
        score.setUser(user);
        score.setBestScore(best);
        return score;
    }
}
