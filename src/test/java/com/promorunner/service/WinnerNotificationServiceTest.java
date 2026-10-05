package com.promorunner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.promorunner.config.AppProperties;
import com.promorunner.dto.LeaderboardEntry;
import com.promorunner.dto.LeaderboardSnapshot;
import com.promorunner.model.CampaignWinnerNotification;
import com.promorunner.model.User;
import com.promorunner.repository.CampaignWinnerNotificationRepository;
import com.promorunner.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WinnerNotificationServiceTest {

    @Mock
    private CampaignService campaignService;
    @Mock
    private LeaderboardService leaderboardService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CampaignWinnerNotificationRepository notificationRepository;
    @Mock
    private MailService mailService;

    private WinnerNotificationService service;

    @BeforeEach
    void setUp() {
        service = new WinnerNotificationService(
                campaignService,
                leaderboardService,
                userRepository,
                notificationRepository,
                mailService
        );
    }

    @Test
    void doesNothingWhenCampaignStillOpen() {
        when(campaignService.hasEnded()).thenReturn(false);

        service.notifyWinnersIfNeeded();

        verify(leaderboardService, never()).getTop(anyInt());
        verify(mailService, never()).sendHtml(anyString(), anyString(), anyString());
    }

    @Test
    void doesNothingWhenWinnerEmailDisabled() {
        when(campaignService.hasEnded()).thenReturn(true);
        when(campaignService.isWinnerEmailEnabled()).thenReturn(false);

        service.notifyWinnersIfNeeded();

        verify(leaderboardService, never()).getTop(anyInt());
    }

    @Test
    void recordsEmptyBoardMarkerOnce() {
        when(campaignService.hasEnded()).thenReturn(true);
        when(campaignService.isWinnerEmailEnabled()).thenReturn(true);
        when(leaderboardService.getTop(3)).thenReturn(new LeaderboardSnapshot(List.of(), 0));
        when(notificationRepository.existsByPlaceRank(0)).thenReturn(false);

        service.notifyWinnersIfNeeded();

        ArgumentCaptor<CampaignWinnerNotification> captor =
                ArgumentCaptor.forClass(CampaignWinnerNotification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getPlaceRank()).isEqualTo(0);
        assertThat(captor.getValue().getPrizeName()).isEqualTo("NONE");
        verify(mailService, never()).sendHtml(anyString(), anyString(), anyString());
    }

    @Test
    void sendsOneEmailPerWinnerAndSkipsAlreadyNotified() {
        when(campaignService.hasEnded()).thenReturn(true);
        when(campaignService.isWinnerEmailEnabled()).thenReturn(true);
        when(leaderboardService.getTop(3)).thenReturn(new LeaderboardSnapshot(List.of(
                new LeaderboardEntry(1, 10L, "gold_user", 9000),
                new LeaderboardEntry(2, 20L, "silver_user", 8000),
                new LeaderboardEntry(3, 30L, "bronze_user", 7000)
        ), 3));
        when(notificationRepository.existsByPlaceRank(1)).thenReturn(false);
        when(notificationRepository.existsByPlaceRank(2)).thenReturn(true);
        when(notificationRepository.existsByPlaceRank(3)).thenReturn(false);
        when(campaignService.prizeForRank(1)).thenReturn("Паметен телефон");
        when(campaignService.prizeForRank(3)).thenReturn("АМСМ комплет за пат");
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, "g@ex.com")));
        when(userRepository.findById(30L)).thenReturn(Optional.of(user(30L, "b@ex.com")));
        when(mailService.render(eq("email/winner"), anyMap())).thenReturn("<html>win</html>");

        service.notifyWinnersIfNeeded();

        verify(mailService, times(2)).sendHtml(anyString(), anyString(), eq("<html>win</html>"));
        verify(mailService).sendHtml(eq("g@ex.com"), anyString(), anyString());
        verify(mailService).sendHtml(eq("b@ex.com"), anyString(), anyString());
        verify(mailService, never()).sendHtml(eq("s@ex.com"), anyString(), anyString());
        verify(notificationRepository, times(2)).save(any(CampaignWinnerNotification.class));
    }

    @Test
    void secondRunSendsNothingWhenAllRanksAlreadyNotified() {
        when(campaignService.hasEnded()).thenReturn(true);
        when(campaignService.isWinnerEmailEnabled()).thenReturn(true);
        when(leaderboardService.getTop(3)).thenReturn(new LeaderboardSnapshot(List.of(
                new LeaderboardEntry(1, 10L, "gold_user", 9000),
                new LeaderboardEntry(2, 20L, "silver_user", 8000),
                new LeaderboardEntry(3, 30L, "bronze_user", 7000)
        ), 3));
        when(notificationRepository.existsByPlaceRank(1)).thenReturn(true);
        when(notificationRepository.existsByPlaceRank(2)).thenReturn(true);
        when(notificationRepository.existsByPlaceRank(3)).thenReturn(true);

        service.notifyWinnersIfNeeded();

        verify(mailService, never()).sendHtml(anyString(), anyString(), anyString());
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void campaignServiceDefaultsDurationToThirtyDays() {
        AppProperties props = new AppProperties();
        props.getCampaign().setDurationDays(30);
        props.getCampaign().setStartAt(java.time.Instant.parse("2026-01-01T00:00:00Z"));

        CampaignService campaign = new CampaignService(props);

        assertThat(campaign.getEndAt()).isEqualTo(java.time.Instant.parse("2026-01-31T00:00:00Z"));
        assertThat(campaign.getPrizes().getFirst()).isEqualTo("Паметен телефон");
        assertThat(campaign.prizeForRank(2)).contains("членска");
    }

    private static User user(long id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setDisplayName("u" + id);
        return user;
    }
}
