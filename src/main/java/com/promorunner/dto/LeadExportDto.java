package com.promorunner.dto;

import java.time.Instant;

public record LeadExportDto(
        long userId,
        String email,
        String displayName,
        Instant consentedAt
) {
}
