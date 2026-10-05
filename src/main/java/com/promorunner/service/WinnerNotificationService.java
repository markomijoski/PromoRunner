package com.promorunner.service;

import com.promorunner.dto.LeaderboardEntry;
import com.promorunner.dto.LeaderboardSnapshot;
import com.promorunner.model.CampaignWinnerNotification;
import com.promorunner.model.User;
import com.promorunner.repository.CampaignWinnerNotificationRepository;
import com.promorunner.repository.UserRepository;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WinnerNotificationService {

    private static final Logger log = LoggerFactory.getLogger(WinnerNotificationService.class);
    private static final int EMPTY_BOARD_SENTINEL_RANK = 0;

    private final CampaignService campaignService;
    private final LeaderboardService leaderboardService;
    private final UserRepository userRepository;
    private final CampaignWinnerNotificationRepository notificationRepository;
    private final MailService mailService;

    public WinnerNotificationService(
            CampaignService campaignService,
            LeaderboardService leaderboardService,
            UserRepository userRepository,
            CampaignWinnerNotificationRepository notificationRepository,
            MailService mailService) {
        this.campaignService = campaignService;
        this.leaderboardService = leaderboardService;
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
        this.mailService = mailService;
    }

    /**
     * Freeze top 3 after campaign end and email each winner once.
     * Safe to call repeatedly — unique place_rank rows make sends idempotent.
     */
    @Transactional
    public void notifyWinnersIfNeeded() {
        if (!campaignService.hasEnded() || !campaignService.isWinnerEmailEnabled()) {
            return;
        }

        LeaderboardSnapshot top = leaderboardService.getTop(3);
        if (top.entries().isEmpty()) {
            if (!notificationRepository.existsByPlaceRank(EMPTY_BOARD_SENTINEL_RANK)) {
                CampaignWinnerNotification marker = new CampaignWinnerNotification();
                marker.setPlaceRank(EMPTY_BOARD_SENTINEL_RANK);
                marker.setPrizeName("NONE");
                notificationRepository.save(marker);
                log.info("Campaign ended with no visible scores; recorded empty-board marker");
            }
            return;
        }

        for (LeaderboardEntry entry : top.entries()) {
            if (entry.rank() < 1 || entry.rank() > 3) {
                continue;
            }
            if (notificationRepository.existsByPlaceRank(entry.rank())) {
                continue;
            }
            sendWinnerEmail(entry);
        }
    }

    private void sendWinnerEmail(LeaderboardEntry entry) {
        User user = userRepository.findById(entry.userId()).orElse(null);
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            log.warn("Skipping winner rank {} — user {} missing email", entry.rank(), entry.userId());
            return;
        }

        String prize = campaignService.prizeForRank(entry.rank());
        String username = entry.displayName();
        String placeLabel = placeLabel(entry.rank());

        String html = mailService.render("email/winner", Map.of(
                "username", username,
                "prizeName", prize,
                "placeRank", entry.rank(),
                "placeLabel", placeLabel
        ));

        mailService.sendHtml(
                user.getEmail(),
                "Честитки! Освоивте награда — AMSM Runner",
                html
        );

        CampaignWinnerNotification notification = new CampaignWinnerNotification();
        notification.setPlaceRank(entry.rank());
        notification.setUser(user);
        notification.setPrizeName(prize);
        notificationRepository.save(notification);

        log.info("Sent winner email for rank {} to user {}", entry.rank(), user.getId());
    }

    static String placeLabel(int rank) {
        return switch (rank) {
            case 1 -> "1. место";
            case 2 -> "2. место";
            case 3 -> "3. место";
            default -> rank + ". место";
        };
    }
}
