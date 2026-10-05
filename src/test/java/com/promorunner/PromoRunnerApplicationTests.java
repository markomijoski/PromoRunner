package com.promorunner;

import com.promorunner.repository.GameSessionRepository;
import com.promorunner.repository.PasswordResetTokenRepository;
import com.promorunner.repository.CampaignWinnerNotificationRepository;
import com.promorunner.repository.ScoreRepository;
import com.promorunner.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class PromoRunnerApplicationTests {

    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private PasswordResetTokenRepository passwordResetTokenRepository;
    @MockitoBean
    private GameSessionRepository gameSessionRepository;
    @MockitoBean
    private ScoreRepository scoreRepository;
    @MockitoBean
    private CampaignWinnerNotificationRepository campaignWinnerNotificationRepository;
    @MockitoBean
    private JavaMailSender javaMailSender;

    @Test
    void contextLoads() {
    }
}
