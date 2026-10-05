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
import org.springframework.data.domain.PageRequest;
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
    void getTopMapsRanksAndDisplayNames() {
        User jane = user(1L, "jane@ex.com", "JaneD");
        User mark = user(2L, "mark@ex.com", "Marko");
        Score s1 = score(jane, 12540);
        Score s2 = score(mark, 9820);

        when(scoreRepository.findTopVisibleByOrderByBestScoreDesc(any(Pageable.class)))
                .thenReturn(List.of(s1, s2));
        when(scoreRepository.countVisiblePlayers()).thenReturn(124L);

        LeaderboardSnapshot snapshot = leaderboardService.getTop();

        assertThat(snapshot.totalPlayers()).isEqualTo(124);
        assertThat(snapshot.entries()).containsExactly(
                new LeaderboardEntry(1, 1L, "JaneD", 12540),
                new LeaderboardEntry(2, 2L, "Marko", 9820)
        );
    }

    @Test
    void getTopRequestsTwentyFive() {
        when(scoreRepository.findTopVisibleByOrderByBestScoreDesc(any(Pageable.class)))
                .thenReturn(List.of());
        when(scoreRepository.countVisiblePlayers()).thenReturn(0L);

        leaderboardService.getTop();

        org.mockito.Mockito.verify(scoreRepository)
                .findTopVisibleByOrderByBestScoreDesc(PageRequest.of(0, 25));
    }

    @Test
    void forViewerAppendsWhenOutsideTop() {
        User top = user(1L, "top@ex.com", "TopPlayer");
        User me = user(99L, "me@ex.com", "MyName");
        when(scoreRepository.findTopVisibleByOrderByBestScoreDesc(any(Pageable.class)))
                .thenReturn(List.of(score(top, 9000)));
        when(scoreRepository.countVisiblePlayers()).thenReturn(40L);
        when(userRepository.existsById(99L)).thenReturn(true);
        when(scoreRepository.findByUserId(99L)).thenReturn(Optional.of(score(me, 100)));
        when(scoreRepository.countByBestScoreGreaterThan(100)).thenReturn(30L);

        LeaderboardSnapshot snapshot = leaderboardService.forViewer(99L);

        assertThat(snapshot.entries()).hasSize(2);
        assertThat(snapshot.entries().get(0)).isEqualTo(new LeaderboardEntry(1, 1L, "TopPlayer", 9000));
        assertThat(snapshot.entries().get(1)).isEqualTo(new LeaderboardEntry(31, 99L, "MyName", 100));
    }

    @Test
    void forViewerDoesNotDuplicateWhenInTop() {
        User me = user(1L, "me@ex.com", "Me");
        when(scoreRepository.findTopVisibleByOrderByBestScoreDesc(any(Pageable.class)))
                .thenReturn(List.of(score(me, 5000)));
        when(scoreRepository.countVisiblePlayers()).thenReturn(10L);

        LeaderboardSnapshot snapshot = leaderboardService.forViewer(1L);

        assertThat(snapshot.entries()).containsExactly(
                new LeaderboardEntry(1, 1L, "Me", 5000)
        );
    }

    @Test
    void displayNameFallsBackWhenMissing() {
        User anon = user(3L, "a@b.com", null);
        when(scoreRepository.findTopVisibleByOrderByBestScoreDesc(any(Pageable.class)))
                .thenReturn(List.of(score(anon, 10)));
        when(scoreRepository.countVisiblePlayers()).thenReturn(1L);

        LeaderboardSnapshot snapshot = leaderboardService.getTop();

        assertThat(snapshot.entries().get(0).displayName()).isEqualTo("Играч");
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
        user.setLeaderboardVisible(true);
        return user;
    }

    private static Score score(User user, int best) {
        Score score = new Score();
        score.setUser(user);
        score.setBestScore(best);
        return score;
    }
}
