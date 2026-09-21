package com.promorunner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.promorunner.exception.UserNotFoundException;
import com.promorunner.model.User;
import com.promorunner.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConsentServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ConsentService consentService;

    @Test
    void recordConsentOptInSetsUnlimitedPlays() {
        User user = userWithPlays(1L, 3);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        consentService.recordConsent(1L, true);

        assertThat(user.isMarketingConsent()).isTrue();
        assertThat(user.getFreePlaysRemaining()).isNull();
        verify(userRepository).save(user);
    }

    @Test
    void recordConsentSkipLeavesPlaysUnchanged() {
        User user = userWithPlays(1L, 3);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        consentService.recordConsent(1L, false);

        assertThat(user.isMarketingConsent()).isFalse();
        assertThat(user.getFreePlaysRemaining()).isEqualTo(3);
        verify(userRepository).save(user);
    }

    @Test
    void withdrawConsentDoesNotRestorePlays() {
        User user = userWithPlays(2L, null);
        user.setMarketingConsent(true);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));

        consentService.withdrawConsent(2L);

        assertThat(user.isMarketingConsent()).isFalse();
        assertThat(user.getFreePlaysRemaining()).isNull();
        verify(userRepository).save(user);
    }

    @Test
    void recordConsentThrowsWhenUserMissing() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> consentService.recordConsent(99L, true))
                .isInstanceOf(UserNotFoundException.class);
    }

    private static User userWithPlays(long id, Integer plays) {
        User user = new User();
        user.setId(id);
        user.setEmail("u" + id + "@ex.com");
        user.setFreePlaysRemaining(plays);
        return user;
    }
}
