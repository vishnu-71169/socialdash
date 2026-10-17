package com.socialdash.account;

public enum Platform {
    FACEBOOK(63_206),
    INSTAGRAM(2_200),
    TWITTER(280),
    LINKEDIN(3_000);

    private final int maxPostLength;

    Platform(int maxPostLength) {
        this.maxPostLength = maxPostLength;
    }

    public int getMaxPostLength() {
        return maxPostLength;
    }
}
