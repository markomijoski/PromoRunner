package com.promorunner.dto;

public record LeaderboardEntry(
        int rank,
        long userId,
        String displayName,
        int score
) {
}
