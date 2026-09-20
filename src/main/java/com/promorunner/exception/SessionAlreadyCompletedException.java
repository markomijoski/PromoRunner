package com.promorunner.exception;

public class SessionAlreadyCompletedException extends RuntimeException {

    public SessionAlreadyCompletedException(long sessionId) {
        super("Game session already completed: " + sessionId);
    }
}
