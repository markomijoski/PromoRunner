package com.promorunner.dto;

import com.promorunner.model.GameConfig;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Passed to Thymeleaf for game.html — config plus runtime player state (PRD §8.2).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameConfigDto {

    private GameConfig config;
    /** -1 = unlimited; mirrors users.free_plays_remaining (null → -1 for JSON). */
    private int playsRemaining;
    private long currentUserId;
}
