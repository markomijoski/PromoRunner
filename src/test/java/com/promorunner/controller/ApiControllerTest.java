package com.promorunner.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.promorunner.dto.EndSessionResult;
import com.promorunner.dto.LeaderboardSnapshot;
import com.promorunner.dto.StartSessionResult;
import com.promorunner.exception.NoPlaysRemainingException;
import com.promorunner.model.UserRole;
import com.promorunner.security.UserPrincipal;
import com.promorunner.service.GameSessionService;
import com.promorunner.service.LeaderboardService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {GameController.class, LeaderboardController.class, GlobalExceptionHandler.class})
class ApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GameSessionService gameSessionService;
    @MockitoBean
    private LeaderboardService leaderboardService;

    private final UserPrincipal player = new UserPrincipal(1L, "p@ex.com", UserRole.PLAYER);

    @Test
    void startSessionReturnsAllowedPayload() throws Exception {
        when(gameSessionService.startSession(1L, "desktop"))
                .thenReturn(new StartSessionResult(42L, true, 2));

        mockMvc.perform(post("/api/game/session/start")
                        .with(user(player))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(42))
                .andExpect(jsonPath("$.canPlay").value(true))
                .andExpect(jsonPath("$.playsRemaining").value(2));
    }

    @Test
    void startSessionReturnsForbiddenWhenNoPlays() throws Exception {
        when(gameSessionService.startSession(eq(1L), anyString()))
                .thenThrow(new NoPlaysRemainingException());

        mockMvc.perform(post("/api/game/session/start")
                        .with(user(player))
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.canPlay").value(false))
                .andExpect(jsonPath("$.reason").value("CONSENT_REQUIRED"));
    }

    @Test
    void endSessionReturnsResult() throws Exception {
        when(gameSessionService.endSession(42L, 1L, 4820, 94200))
                .thenReturn(new EndSessionResult(4820, true, 3, 124));

        mockMvc.perform(post("/api/game/session/42/end")
                        .with(user(player))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":4820,\"durationMs\":94200}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rank").value(3))
                .andExpect(jsonPath("$.personalBest").value(true));
    }

    @Test
    void leaderboardReturnsSnapshot() throws Exception {
        when(leaderboardService.forViewer(1L))
                .thenReturn(new LeaderboardSnapshot(List.of(), 0));

        mockMvc.perform(get("/api/leaderboard").with(user(player)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPlayers").value(0));
    }
}
