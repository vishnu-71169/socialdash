package com.socialdash.integration;

/** Failure talking to a social platform. {@code retryable} distinguishes timeouts/rate limits from hard rejections. */
public class PlatformApiException extends RuntimeException {

    private final boolean retryable;

    public PlatformApiException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
