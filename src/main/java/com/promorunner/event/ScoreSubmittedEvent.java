package com.promorunner.event;

/** Published when a game session ends with a score; leaderboard WebSocket listens. */
public record ScoreSubmittedEvent(
        long userId,
        long sessionId,
        int score,
        boolean personalBest
) {
}
