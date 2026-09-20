package com.promorunner.dto;

public record EndSessionResult(
        int score,
        boolean personalBest,
        int rank,
        long totalPlayers
) {
}
