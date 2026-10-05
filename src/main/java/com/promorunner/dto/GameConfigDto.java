package com.promorunner.dto;

/**
 * Runtime player state for game.html (window.GAME_CONFIG).
 * Live gameplay assets/rules live in static/js/game.js.
 */
public record GameConfigDto(
        /** -1 = unlimited; mirrors users.free_plays_remaining (null → -1 for JSON). */
        int playsRemaining
) {
}
