package com.promorunner;

import com.promorunner.config.AppProperties;
import com.promorunner.config.BrandProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({BrandProperties.class, AppProperties.class})
public class PromoRunnerApplication {

    public static void main(String[] args) {
        SpringApplication.run(PromoRunnerApplication.class, args);
    }
}
