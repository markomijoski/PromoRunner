package com.promorunner.service;

import com.promorunner.exception.UserNotFoundException;
import com.promorunner.model.MarketingConsent;
import com.promorunner.model.User;
import com.promorunner.repository.MarketingConsentRepository;
import com.promorunner.repository.UserRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsentService {

    private final MarketingConsentRepository marketingConsentRepository;
    private final UserRepository userRepository;

    public ConsentService(
            MarketingConsentRepository marketingConsentRepository,
            UserRepository userRepository) {
        this.marketingConsentRepository = marketingConsentRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void recordConsent(long userId, boolean consented, String userAgent) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        MarketingConsent consent = new MarketingConsent();
        consent.setUser(user);
        consent.setConsented(consented);
        consent.setUserAgent(userAgent);
        marketingConsentRepository.save(consent);

        if (consented) {
            user.setFreePlaysRemaining(null);
            userRepository.save(user);
        }
        // Skip: leave freePlaysRemaining as-is (typically 3); never reset unlimited
    }

    @Transactional
    public void withdrawConsent(long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        MarketingConsent consent = new MarketingConsent();
        consent.setUser(user);
        consent.setConsented(false);
        consent.setWithdrawnAt(Instant.now());
        marketingConsentRepository.save(consent);
        // Does NOT restore free plays — user keeps unlimited if they had it
    }
}
