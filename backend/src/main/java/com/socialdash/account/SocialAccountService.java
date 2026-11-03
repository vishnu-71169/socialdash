package com.socialdash.account;

import com.socialdash.account.AccountDtos.AccountResponse;
import com.socialdash.account.AccountDtos.ConnectAccountRequest;
import com.socialdash.account.AccountDtos.ConnectMetaRequest;
import com.socialdash.analytics.AnalyticsCollector;
import com.socialdash.analytics.AnalyticsSnapshot;
import com.socialdash.analytics.AnalyticsSnapshotRepository;
import com.socialdash.common.ApiException;
import com.socialdash.config.AppProperties;
import com.socialdash.engagement.EngagementService;
import com.socialdash.integration.PlatformApiException;
import com.socialdash.integration.meta.MetaGraphClient;
import com.socialdash.security.TokenCipher;
import com.socialdash.user.UserService;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SocialAccountService {

    private static final Logger log = LoggerFactory.getLogger(SocialAccountService.class);

    /** Days of history generated when a simulated account is connected. */
    static final int BACKFILL_DAYS = 120;

    private final SocialAccountRepository accounts;
    private final AnalyticsSnapshotRepository snapshots;
    private final AnalyticsCollector collector;
    private final EngagementService engagementService;
    private final TokenCipher tokenCipher;
    private final UserService userService;
    private final MetaGraphClient meta;
    private final AppProperties.Meta metaConfig;

    public SocialAccountService(SocialAccountRepository accounts, AnalyticsSnapshotRepository snapshots,
                                AnalyticsCollector collector, EngagementService engagementService,
                                TokenCipher tokenCipher, UserService userService, MetaGraphClient meta,
                                AppProperties props) {
        this.accounts = accounts;
        this.snapshots = snapshots;
        this.collector = collector;
        this.engagementService = engagementService;
        this.tokenCipher = tokenCipher;
        this.userService = userService;
        this.meta = meta;
        this.metaConfig = props.meta();
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> list(Long userId) {
        return accounts.findByUserIdOrderByConnectedAt(userId).stream().map(this::toResponse).toList();
    }

    /** Connects a simulated account with generated history. Live Meta accounts use {@link #connectMeta}. */
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

    /**
     * Connects a live Instagram professional account or Facebook Page. The token is checked against Meta first and
     * stored encrypted. If fetching history fails afterwards the account stays connected and fills in on later syncs.
     */
    @Transactional
    public AccountResponse connectMeta(Long userId, ConnectMetaRequest req) {
        if (req.platform() != Platform.INSTAGRAM && req.platform() != Platform.FACEBOOK) {
            throw ApiException.badRequest("Only Instagram and Facebook accounts can be connected through Meta");
        }
        MetaGraphClient.Profile profile;
        try {
            profile = meta.verify(req.platform(), req.accountId().trim(), req.accessToken().trim());
        } catch (PlatformApiException e) {
            throw platformError("Meta didn't accept this account and token", e);
        }
        if (accounts.existsByUserIdAndPlatformAndPlatformAccountId(userId, req.platform(), profile.id())) {
            throw ApiException.conflict(profile.handle() + " is already connected on " + req.platform());
        }
        SocialAccount account = new SocialAccount(userService.get(userId), req.platform(), profile.id(),
                profile.handle(), profile.displayName(), false);
        account.setAccessTokenEncrypted(tokenCipher.encrypt(profile.accessToken()));
        accounts.save(account);

        try {
            collector.backfill(account, metaConfig.backfillDays());
            engagementService.backfill(account);
        } catch (PlatformApiException e) {
            log.warn("Connected {} but initial sync failed: {}", profile.handle(), e.getMessage());
        }
        return toResponse(account);
    }

    @Transactional
    public AccountResponse sync(Long userId, Long accountId) {
        SocialAccount account = owned(userId, accountId);
        try {
            collector.collectAccount(account);
            engagementService.syncAccount(account);
        } catch (PlatformApiException e) {
            throw platformError("Sync failed", e);
        }
        return toResponse(account);
    }

    private static ApiException platformError(String what, PlatformApiException e) {
        return new ApiException(e.isRetryable() ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_REQUEST,
                what + ": " + e.getMessage());
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
