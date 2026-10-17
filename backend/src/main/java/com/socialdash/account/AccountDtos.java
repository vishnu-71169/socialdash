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
