package com.promorunner.dto;

public record LeadExportDto(
        long userId,
        String email,
        String displayName
) {
}
