package com.promorunner.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * Root mapping for classpath:game-config.json (PRD §4.7 / §8.2).
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GameConfig {

    private int baseScrollSpeed;
    private CharacterAsset character;
    private List<ObstacleAsset> obstacles = new ArrayList<>();

    public void setObstacles(List<ObstacleAsset> obstacles) {
        this.obstacles = obstacles != null ? obstacles : new ArrayList<>();
    }
}
