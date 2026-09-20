package com.promorunner.dto;

import java.time.Instant;
import java.util.List;

public record LeaderboardUpdateMessage(
        String type,
        List<LeaderboardEntry> entries,
        long totalPlayers,
        Instant updatedAt
) {
    public static LeaderboardUpdateMessage of(LeaderboardSnapshot snapshot) {
        return new LeaderboardUpdateMessage(
                "LEADERBOARD_UPDATE",
                snapshot.entries(),
                snapshot.totalPlayers(),
                Instant.now()
        );
    }
}
