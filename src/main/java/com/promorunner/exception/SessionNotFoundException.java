package com.promorunner.exception;

public class SessionNotFoundException extends RuntimeException {

    public SessionNotFoundException(long sessionId) {
        super("Game session not found: " + sessionId);
    }
}
