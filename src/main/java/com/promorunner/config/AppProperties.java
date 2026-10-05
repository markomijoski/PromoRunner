package com.promorunner.config;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** Public base URL used in transactional emails. */
    private String baseUrl = "http://localhost:8080";

    private String uploadDir = "src/main/resources/static/images";

    private Campaign campaign = new Campaign();

    @Getter
    @Setter
    public static class Campaign {
        /** When unset, resolved to application boot time. */
        private Instant startAt;

        /** When unset, resolved to startAt + durationDays. */
        private Instant endAt;

        private int durationDays = 30;

        private boolean winnerEmailEnabled = true;

        private Prizes prizes = new Prizes();
    }

    @Getter
    @Setter
    public static class Prizes {
        private String first = "Паметен телефон";
        private String second = "Годишна членска карта АМСМ";
        private String third = "АМСМ комплет за пат";
    }
}
