package com.socialdash.integration;

import com.socialdash.account.SocialAccount;
import org.springframework.stereotype.Component;

/** Picks the client for an account. Live integrations plug in here once platform API access is approved. */
@Component
public class PlatformClientRegistry {

    private final SimulatedPlatformClient simulated;

    public PlatformClientRegistry(SimulatedPlatformClient simulated) {
        this.simulated = simulated;
    }

    public SocialPlatformClient forAccount(SocialAccount account) {
        if (account.isSimulated()) {
            return simulated;
        }
        throw new PlatformApiException("Live " + account.getPlatform() + " integration is not configured", false);
    }

    /** Client used for keyword search. Until a search-capable API is configured, mentions are simulated. */
    public SocialPlatformClient forListening() {
        return simulated;
    }
}
