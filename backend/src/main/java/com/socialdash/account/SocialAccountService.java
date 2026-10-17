package com.socialdash.account;

import com.socialdash.account.AccountDtos.AccountResponse;
import com.socialdash.account.AccountDtos.ConnectAccountRequest;
import com.socialdash.analytics.AnalyticsCollector;
import com.socialdash.analytics.AnalyticsSnapshot;
import com.socialdash.analytics.AnalyticsSnapshotRepository;
import com.socialdash.common.ApiException;
import com.socialdash.engagement.EngagementService;
import com.socialdash.security.TokenCipher;
import com.socialdash.user.UserService;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SocialAccountService {

    /** Days of history generated when a simulated account is connected. */
    static final int BACKFILL_DAYS = 120;

    private final SocialAccountRepository accounts;
    private final AnalyticsSnapshotRepository snapshots;
    private final AnalyticsCollector collector;
    private final EngagementService engagementService;
    private final TokenCipher tokenCipher;
    private final UserService userService;

    public SocialAccountService(SocialAccountRepository accounts, AnalyticsSnapshotRepository snapshots,
                                AnalyticsCollector collector, EngagementService engagementService,
                                TokenCipher tokenCipher, UserService userService) {
        this.accounts = accounts;
        this.snapshots = snapshots;
        this.collector = collector;
        this.engagementService = engagementService;
        this.tokenCipher = tokenCipher;
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> list(Long userId) {
        return accounts.findByUserIdOrderByConnectedAt(userId).stream().map(this::toResponse).toList();
    }

    /**
     * Connects a simulated account. A live connection would instead go through the platform's OAuth flow and store
     * the returned token here (encrypted) - see README "Adding a real platform".
     */
    @Transactional
    public AccountResponse connectSimulated(Long userId, ConnectAccountRequest req) {
        String handle = "@" + req.handle().trim().replaceFirst("^@", "");
        if (accounts.existsByUserIdAndPlatformAndHandleIgnoreCase(userId, req.platform(), handle)) {
            throw ApiException.conflict(handle + " is already connected on " + req.platform());
        }
        String displayName = req.displayName() == null || req.displayName().isBlank()
                ? handle.substring(1) : req.displayName().trim();
        String externalId = "sim-" + req.platform().name().toLowerCase(Locale.ROOT) + "-" + UUID.randomUUID();
        SocialAccount account = new SocialAccount(userService.get(userId), req.platform(), externalId, handle,
                displayName, true);
        account.setAccessTokenEncrypted(tokenCipher.encrypt("sim-token-" + UUID.randomUUID()));
        accounts.save(account);

        collector.backfill(account, BACKFILL_DAYS);
        engagementService.backfill(account);
        return toResponse(account);
    }

    @Transactional
    public AccountResponse sync(Long userId, Long accountId) {
        SocialAccount account = owned(userId, accountId);
        collector.collectAccount(account);
        engagementService.syncAccount(account);
        return toResponse(account);
    }

    @Transactional
    public void disconnect(Long userId, Long accountId) {
        accounts.delete(owned(userId, accountId));
    }

    private SocialAccount owned(Long userId, Long accountId) {
        return accounts.findByIdAndUserId(accountId, userId).orElseThrow(() -> ApiException.notFound("Account"));
    }

    private AccountResponse toResponse(SocialAccount a) {
        Long followers = snapshots.findTopBySocialAccountIdOrderBySnapshotDateDesc(a.getId())
                .map(AnalyticsSnapshot::getFollowers).orElse(null);
        return AccountResponse.from(a, followers);
    }
}
