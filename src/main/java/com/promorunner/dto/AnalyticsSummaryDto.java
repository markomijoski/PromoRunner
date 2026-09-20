package com.promorunner.dto;

public record AnalyticsSummaryDto(
        long totalPlayers,
        long totalLeads,
        double averageBestScore,
        long totalPlays
) {
}
