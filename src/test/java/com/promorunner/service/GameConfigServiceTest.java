package com.promorunner.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.promorunner.model.ObstacleAsset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import tools.jackson.databind.ObjectMapper;

class GameConfigServiceTest {

    private GameConfigService gameConfigService;

    @BeforeEach
    void setUp() {
        gameConfigService = new GameConfigService(new DefaultResourceLoader(), new ObjectMapper());
    }

    @Test
    void loadsClasspathGameConfig() {
        assertThat(gameConfigService.getConfig().getBaseScrollSpeed()).isEqualTo(300);
        assertThat(gameConfigService.getConfig().getObstacles()).hasSize(4);
    }

    @Test
    void eligibleObstaclesFilterByScoreAndActive() {
        assertThat(gameConfigService.getEligibleObstacles(0))
                .extracting(ObstacleAsset::getKey)
                .containsExactlyInAnyOrder("obs_h_low_wall", "obs_v_wall");

        assertThat(gameConfigService.getEligibleObstacles(300))
                .extracting(ObstacleAsset::getKey)
                .containsExactlyInAnyOrder("obs_h_low_wall", "obs_h_high_wall", "obs_v_wall");

        assertThat(gameConfigService.getEligibleObstacles(600))
                .extracting(ObstacleAsset::getKey)
                .containsExactlyInAnyOrder(
                        "obs_h_low_wall", "obs_h_high_wall", "obs_v_wall", "obs_h_low_wide");
    }
}
