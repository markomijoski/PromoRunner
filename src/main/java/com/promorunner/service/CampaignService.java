package com.promorunner.service;

import com.promorunner.config.AppProperties;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class CampaignService {

    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Europe/Skopje");
    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("mk"));

    private final AppProperties appProperties;
    private final Instant startAt;
    private final Instant endAt;

    public CampaignService(AppProperties appProperties) {
        this.appProperties = appProperties;
        AppProperties.Campaign campaign = appProperties.getCampaign();
        Instant start = campaign.getStartAt();
        if (start == null) {
            start = Instant.now();
        }
        this.startAt = start;

        Instant end = campaign.getEndAt();
        if (end == null) {
            end = start.plus(Math.max(1, campaign.getDurationDays()), ChronoUnit.DAYS);
        }
        this.endAt = end;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public boolean isOpen() {
        Instant now = Instant.now();
        return !now.isBefore(startAt) && now.isBefore(endAt);
    }

    public boolean hasEnded() {
        return !Instant.now().isBefore(endAt);
    }

    public boolean isWinnerEmailEnabled() {
        return appProperties.getCampaign().isWinnerEmailEnabled();
    }

    public String prizeForRank(int rank) {
        AppProperties.Prizes prizes = appProperties.getCampaign().getPrizes();
        return switch (rank) {
            case 1 -> prizes.getFirst();
            case 2 -> prizes.getSecond();
            case 3 -> prizes.getThird();
            default -> throw new IllegalArgumentException("Unsupported prize rank: " + rank);
        };
    }

    public AppProperties.Prizes getPrizes() {
        return appProperties.getCampaign().getPrizes();
    }

    public String formatStartDate() {
        return format(startAt);
    }

    public String formatEndDate() {
        return format(endAt);
    }

    private static String format(Instant instant) {
        return DISPLAY_FORMAT.format(instant.atZone(DISPLAY_ZONE));
    }
}
