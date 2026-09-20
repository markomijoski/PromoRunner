package com.promorunner.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** Public base URL used in magic-link emails. */
    private String baseUrl = "http://localhost:8080";
}
