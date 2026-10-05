package com.promorunner;

import com.promorunner.config.AppProperties;
import com.promorunner.config.BrandProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties({BrandProperties.class, AppProperties.class})
@EnableScheduling
public class PromoRunnerApplication {

    public static void main(String[] args) {
        SpringApplication.run(PromoRunnerApplication.class, args);
    }
}
