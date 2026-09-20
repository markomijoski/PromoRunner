package com.promorunner.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ObstacleAsset {

    private String key;
    /** OBSTACLE_HORIZONTAL | OBSTACLE_VERTICAL */
    private String type;
    private String imageUrl;
    private boolean active;
    private int minScoreToSpawn;
    private int spawnWeight;
    private int minUnits;
    private int maxUnits;
    /** LOW | HIGH | null (vertical walls) */
    private String positionType;
}
