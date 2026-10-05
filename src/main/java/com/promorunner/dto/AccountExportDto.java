package com.promorunner.dto;

import java.time.Instant;
import java.util.List;

public record AccountExportDto(
        long userId,
        String email,
        String username,
        boolean emailVerified,
        boolean marketingConsent,
        boolean leaderboardVisible,
        Integer freePlaysRemaining,
        Instant createdAt,
        Instant lastSeenAt,
        ScoreExport score,
        List<SessionExport> sessions
) {
    public record ScoreExport(Integer bestScore, Integer totalPlays, Instant lastPlayedAt) {
    }

    public record SessionExport(
            long id,
            Instant startedAt,
            Instant endedAt,
            Integer finalScore,
            Integer durationMs,
            String deviceType,
            boolean completed
    ) {
    }
}
