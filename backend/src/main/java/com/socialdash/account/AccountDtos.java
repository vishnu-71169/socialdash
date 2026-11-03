package com.socialdash.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class AccountDtos {

    private AccountDtos() {}

    public record ConnectAccountRequest(
            @NotNull Platform platform,
            @NotBlank @Size(max = 50) @Pattern(regexp = "^@?[A-Za-z0-9_.]+$", message = "letters, numbers, _ and . only")
            String handle,
            @Size(max = 150) String displayName) {}

    /** A live Instagram professional account or Facebook Page, connected with a token from the Meta dashboard. */
    public record ConnectMetaRequest(
            @NotNull Platform platform,
            @NotBlank @Size(max = 64) @Pattern(regexp = "^[0-9]+$", message = "numbers only") String accountId,
            @NotBlank @Size(max = 2048) String accessToken) {}

    public record AccountResponse(
            Long id,
            Platform platform,
            String handle,
            String displayName,
            boolean simulated,
            Instant connectedAt,
            Instant lastSyncedAt,
            Long followers) {

        public static AccountResponse from(SocialAccount a, Long followers) {
            return new AccountResponse(a.getId(), a.getPlatform(), a.getHandle(), a.getDisplayName(),
                    a.isSimulated(), a.getConnectedAt(), a.getLastSyncedAt(), followers);
        }
    }
}
