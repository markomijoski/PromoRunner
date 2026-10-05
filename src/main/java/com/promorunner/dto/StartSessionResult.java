package com.promorunner.dto;

public record StartSessionResult(
        long sessionId,
        int playsRemaining
) {
    /** playsRemaining: -1 means unlimited. */
}
