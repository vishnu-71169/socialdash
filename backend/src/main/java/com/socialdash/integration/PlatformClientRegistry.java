package com.socialdash.integration;

import com.socialdash.account.Platform;
import com.socialdash.account.SocialAccount;
import com.socialdash.integration.meta.MetaGraphClient;
import org.springframework.stereotype.Component;

/** Picks the client for an account: the simulator, or the Meta Graph API for live Instagram/Facebook accounts. */
@Component
public class PlatformClientRegistry {

    private final SimulatedPlatformClient simulated;
    private final MetaGraphClient meta;

    public PlatformClientRegistry(SimulatedPlatformClient simulated, MetaGraphClient meta) {
        this.simulated = simulated;
        this.meta = meta;
    }

    public SocialPlatformClient forAccount(SocialAccount account) {
        if (account.isSimulated()) {
            return simulated;
        }
        if (account.getPlatform() == Platform.INSTAGRAM || account.getPlatform() == Platform.FACEBOOK) {
            return meta;
        }
        throw new PlatformApiException("Live " + account.getPlatform() + " integration is not configured", false);
    }

    /** Client used for keyword search. The Meta APIs offer no keyword search, so mentions stay simulated. */
    public SocialPlatformClient forListening() {
        return simulated;
    }
}
