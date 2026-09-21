package com.promorunner.controller;

import com.promorunner.dto.LeaderboardSnapshot;
import com.promorunner.security.CurrentUser;
import com.promorunner.service.LeaderboardService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/leaderboard")
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    public LeaderboardController(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    @GetMapping
    public LeaderboardSnapshot top() {
        if (CurrentUser.isAuthenticated()) {
            return leaderboardService.forViewer(CurrentUser.requireId());
        }
        return leaderboardService.getTop();
    }

    @GetMapping("/me")
    public ResponseEntity<?> me() {
        return leaderboardService.getMyRank(CurrentUser.requireId())
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.ok(Map.of(
                        "rank", null,
                        "message", "No score yet"
                )));
    }
}
