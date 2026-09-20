package com.promorunner.exception;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(long userId) {
        super("User not found: " + userId);
    }
}
