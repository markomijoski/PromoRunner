package com.promorunner.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CharacterAsset {

    private String runImageUrl;
    private int runFrameCount;
    private String jumpImageUrl;
    private String fallImageUrl;
    private String slideImageUrl;
}
