package com.promorunner.dto;

public record SessionStartResponse(
        Long sessionId,
        boolean canPlay,
        int playsRemaining,
        String reason
) {
    public static SessionStartResponse allowed(long sessionId, int playsRemaining) {
        return new SessionStartResponse(sessionId, true, playsRemaining, null);
    }

    public static SessionStartResponse blocked(int playsRemaining, String reason) {
        return new SessionStartResponse(null, false, playsRemaining, reason);
    }
}
