package com.promorunner.dto;

public record ScoreUpdateResult(
        int score,
        int bestScore,
        boolean personalBest,
        int totalPlays
) {
}
