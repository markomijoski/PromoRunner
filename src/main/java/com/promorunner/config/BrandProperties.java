package com.promorunner.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "brand")
public class BrandProperties {

    private String name = "AMSM Runner";
    private String campaign = "Останете фокусирани на патот";
    private String primaryColor = "#FFD200";
    private String secondaryColor = "#0A0A0A";
    private String backgroundColor = "#FFFFFF";
    private String consentLabelText =
            "Се согласувам да добивам маркетинг пораки од АМСМ. Можам да се отпишам во секое време.";
}
