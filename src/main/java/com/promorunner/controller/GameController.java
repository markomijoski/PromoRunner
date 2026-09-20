package com.promorunner.controller;

import com.promorunner.dto.EndSessionRequest;
import com.promorunner.dto.EndSessionResult;
import com.promorunner.dto.SessionStartResponse;
import com.promorunner.dto.StartSessionResult;
import com.promorunner.exception.NoPlaysRemainingException;
import com.promorunner.security.CurrentUser;
import com.promorunner.service.GameSessionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/game")
public class GameController {

    private final GameSessionService gameSessionService;

    public GameController(GameSessionService gameSessionService) {
        this.gameSessionService = gameSessionService;
    }

    @PostMapping("/session/start")
    public ResponseEntity<SessionStartResponse> startSession(
            @RequestParam(value = "deviceType", required = false, defaultValue = "desktop") String deviceType) {
        try {
            StartSessionResult result = gameSessionService.startSession(CurrentUser.requireId(), deviceType);
            return ResponseEntity.ok(SessionStartResponse.allowed(result.sessionId(), result.playsRemaining()));
        } catch (NoPlaysRemainingException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(SessionStartResponse.blocked(0, "CONSENT_REQUIRED"));
        }
    }

    @PostMapping("/session/{id}/end")
    public EndSessionResult endSession(
            @PathVariable("id") long sessionId,
            @Valid @RequestBody EndSessionRequest request) {
        return gameSessionService.endSession(
                sessionId,
                CurrentUser.requireId(),
                request.score(),
                request.durationMs()
        );
    }
}
