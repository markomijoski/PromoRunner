package com.promorunner.job;

import com.promorunner.service.WinnerNotificationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WinnerNotificationJob {

    private final WinnerNotificationService winnerNotificationService;

    public WinnerNotificationJob(WinnerNotificationService winnerNotificationService) {
        this.winnerNotificationService = winnerNotificationService;
    }

    @Scheduled(fixedDelayString = "${app.campaign.winner-job-delay-ms:300000}")
    public void run() {
        winnerNotificationService.notifyWinnersIfNeeded();
    }
}
