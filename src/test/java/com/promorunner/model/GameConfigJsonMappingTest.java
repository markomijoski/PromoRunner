package com.promorunner.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.ObjectMapper;

class GameConfigJsonMappingTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mapsClasspathGameConfigJson() throws Exception {
        ClassPathResource resource = new ClassPathResource("game-config.json");
        GameConfig config = objectMapper.readValue(resource.getInputStream(), GameConfig.class);

        assertThat(config.getBaseScrollSpeed()).isEqualTo(300);
        assertThat(config.getCharacter()).isNotNull();
        assertThat(config.getCharacter().getRunFrameCount()).isEqualTo(4);
        assertThat(config.getCharacter().getRunImageUrl()).isEqualTo("/images/character/run.png");
        assertThat(config.getObstacles()).hasSize(4);

        ObstacleAsset vertical = config.getObstacles().stream()
                .filter(o -> "obs_v_wall".equals(o.getKey()))
                .findFirst()
                .orElseThrow();
        assertThat(vertical.getType()).isEqualTo("OBSTACLE_VERTICAL");
        assertThat(vertical.getPositionType()).isNull();
        assertThat(vertical.isActive()).isTrue();

        ObstacleAsset high = config.getObstacles().stream()
                .filter(o -> "obs_h_high_wall".equals(o.getKey()))
                .findFirst()
                .orElseThrow();
        assertThat(high.getPositionType()).isEqualTo("HIGH");
        assertThat(high.getMinScoreToSpawn()).isEqualTo(300);
    }
}
