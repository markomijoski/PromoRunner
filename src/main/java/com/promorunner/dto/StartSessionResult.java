package com.promorunner.dto;

public record StartSessionResult(
        long sessionId,
        boolean canPlay,
        int playsRemaining
) {
    /** playsRemaining: -1 means unlimited. */
}
