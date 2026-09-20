package com.promorunner.service;

import com.promorunner.model.GameConfig;
import com.promorunner.model.ObstacleAsset;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class GameConfigService {

    private final GameConfig config;

    public GameConfigService(ResourceLoader resourceLoader, ObjectMapper objectMapper) {
        Resource resource = resourceLoader.getResource("classpath:game-config.json");
        try (InputStream in = resource.getInputStream()) {
            this.config = objectMapper.readValue(in, GameConfig.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load classpath:game-config.json", e);
        }
    }

    public GameConfig getConfig() {
        return config;
    }

    public List<ObstacleAsset> getEligibleObstacles(int currentScore) {
        return config.getObstacles().stream()
                .filter(o -> o.isActive() && o.getMinScoreToSpawn() <= currentScore)
                .toList();
    }
}
