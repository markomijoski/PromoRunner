package com.promorunner.dto;

import java.util.List;

public record LeaderboardSnapshot(
        List<LeaderboardEntry> entries,
        long totalPlayers
) {
}
