package com.promorunner.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record EndSessionRequest(
        @NotNull @Min(0) Integer score,
        @NotNull @Min(0) Integer durationMs
) {
}
