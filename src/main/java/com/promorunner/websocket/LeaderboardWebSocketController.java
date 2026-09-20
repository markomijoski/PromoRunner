package com.promorunner.websocket;

import com.promorunner.dto.LeaderboardUpdateMessage;
import com.promorunner.event.ScoreSubmittedEvent;
import com.promorunner.service.LeaderboardService;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

@Controller
public class LeaderboardWebSocketController {

    private final LeaderboardService leaderboardService;
    private final SimpMessagingTemplate messagingTemplate;

    public LeaderboardWebSocketController(
            LeaderboardService leaderboardService,
            SimpMessagingTemplate messagingTemplate) {
        this.leaderboardService = leaderboardService;
        this.messagingTemplate = messagingTemplate;
    }

    @SubscribeMapping("/leaderboard/ping")
    public LeaderboardUpdateMessage ping() {
        return LeaderboardUpdateMessage.of(leaderboardService.getTopTen());
    }

    @EventListener
    public void onScoreSubmitted(ScoreSubmittedEvent event) {
        messagingTemplate.convertAndSend(
                "/topic/leaderboard",
                LeaderboardUpdateMessage.of(leaderboardService.getTopTen())
        );
    }
}
