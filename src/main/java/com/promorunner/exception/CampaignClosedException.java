package com.promorunner.exception;

public class CampaignClosedException extends RuntimeException {

    public CampaignClosedException() {
        super("Натпреварот заврши");
    }
}
