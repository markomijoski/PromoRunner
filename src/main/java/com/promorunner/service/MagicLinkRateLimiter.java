package com.promorunner.service;

import com.promorunner.exception.TooManyRequestsException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Simple in-memory rate limit: max 5 magic-link requests per email per 10 minutes.
 */
@Component
public class MagicLinkRateLimiter {

    private static final int MAX_REQUESTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(10);

    private final Map<String, Deque<Instant>> attemptsByEmail = new ConcurrentHashMap<>();

    public void check(String normalizedEmail) {
        Instant now = Instant.now();
        Deque<Instant> attempts = attemptsByEmail.computeIfAbsent(normalizedEmail, k -> new ArrayDeque<>());
        synchronized (attempts) {
            while (!attempts.isEmpty() && attempts.peekFirst().isBefore(now.minus(WINDOW))) {
                attempts.removeFirst();
            }
            if (attempts.size() >= MAX_REQUESTS) {
                throw new TooManyRequestsException(
                        "Too many login requests for this email. Try again later.");
            }
            attempts.addLast(now);
        }
    }
}
