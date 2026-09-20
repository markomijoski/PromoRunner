package com.promorunner.exception;

public class NoPlaysRemainingException extends RuntimeException {

    public NoPlaysRemainingException() {
        super("No free plays remaining; marketing consent required");
    }
}
