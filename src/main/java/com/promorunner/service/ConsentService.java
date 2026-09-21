package com.promorunner.service;

import com.promorunner.exception.UserNotFoundException;
import com.promorunner.model.User;
import com.promorunner.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsentService {

    private final UserRepository userRepository;

    public ConsentService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public void recordConsent(long userId, boolean consented) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        user.setMarketingConsent(consented);
        if (consented) {
            user.setFreePlaysRemaining(null);
        }
        // Skip: leave freePlaysRemaining as-is (typically 3); never reset unlimited
        userRepository.save(user);
    }

    @Transactional
    public void withdrawConsent(long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        user.setMarketingConsent(false);
        userRepository.save(user);
        // Does NOT restore free plays — user keeps unlimited if they had it
    }
}
