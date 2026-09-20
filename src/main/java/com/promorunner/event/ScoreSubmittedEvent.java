package com.promorunner.event;

/**
 * Published when a game session ends with a score. Leaderboard WS listens later.
 */
public record ScoreSubmittedEvent(
        long userId,
        long sessionId,
        int score,
        boolean personalBest
) {
}
