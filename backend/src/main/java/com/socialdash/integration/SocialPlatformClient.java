package com.socialdash.integration;

import com.socialdash.account.Platform;
import com.socialdash.account.SocialAccount;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Boundary to a social network's API. Each real integration (Graph API, LinkedIn API, ...) implements this;
 * {@link SimulatedPlatformClient} backs simulated accounts. Implementations throw {@link PlatformApiException}.
 */
public interface SocialPlatformClient {

    record DailyMetrics(long followers, long impressions, long likes, long comments, long shares) {}

    record PublishResult(String externalPostId, String url) {}

    record PostMetrics(long impressions, long likes, long comments, long shares) {}

    record RawMention(Platform platform, String externalId, String authorHandle, String content, String url,
                      Instant detectedAt) {}

    enum RawEngagementType { COMMENT, MENTION, DIRECT_MESSAGE }

    record RawEngagement(String externalId, RawEngagementType type, String authorName, String authorHandle,
                         String content, Instant receivedAt) {}

    /** Metrics for one calendar day (UTC). For today, values cover the day so far. */
    DailyMetrics fetchDailyMetrics(SocialAccount account, LocalDate date);

    /**
     * Publishes content. {@code idempotencyKey} must be stable across retries of the same publication so that a
     * retry after an ambiguous failure (e.g. a timeout after the platform accepted the post) never double-posts.
     */
    PublishResult publish(SocialAccount account, String content, String idempotencyKey);

    PostMetrics fetchPostMetrics(SocialAccount account, String externalPostId, Instant publishedAt);

    List<RawMention> searchMentions(String keyword, Platform platform, Instant since, Instant until);

    List<RawEngagement> fetchEngagements(SocialAccount account, Instant since, Instant until);

    void reply(SocialAccount account, String engagementExternalId, String message);
}
