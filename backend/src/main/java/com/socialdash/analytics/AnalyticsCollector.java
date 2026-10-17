package com.socialdash.analytics;

import com.socialdash.account.SocialAccount;
import com.socialdash.account.SocialAccountRepository;
import com.socialdash.integration.PlatformApiException;
import com.socialdash.integration.PlatformClientRegistry;
import com.socialdash.integration.SocialPlatformClient.DailyMetrics;
import com.socialdash.integration.SocialPlatformClient.PostMetrics;
import com.socialdash.post.PostPublication;
import com.socialdash.post.PostPublicationRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Pulls metrics from platforms and stores them as history: one daily snapshot per account (today's row is refreshed
 * in place as the day goes on) and periodic per-post data points for recently published posts.
 */
@Component
public class AnalyticsCollector {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsCollector.class);
    /** Gap-fill limit when the collector has been down for a while. */
    private static final int MAX_CATCH_UP_DAYS = 7;
    private static final Duration POST_TRACKING_WINDOW = Duration.ofDays(14);
    private static final Duration POST_HISTORY_INTERVAL = Duration.ofMinutes(55);

    private final SocialAccountRepository accounts;
    private final AnalyticsSnapshotRepository snapshots;
    private final PostAnalyticsRepository postAnalytics;
    private final PostPublicationRepository publications;
    private final PlatformClientRegistry clients;
    private final TransactionTemplate tx;

    public AnalyticsCollector(SocialAccountRepository accounts, AnalyticsSnapshotRepository snapshots,
                              PostAnalyticsRepository postAnalytics, PostPublicationRepository publications,
                              PlatformClientRegistry clients, TransactionTemplate tx) {
        this.accounts = accounts;
        this.snapshots = snapshots;
        this.postAnalytics = postAnalytics;
        this.publications = publications;
        this.clients = clients;
        this.tx = tx;
    }

    @Scheduled(fixedDelayString = "${app.scheduler.analytics-interval-ms:120000}", initialDelay = 10000)
    public void collectAll() {
        for (SocialAccount account : accounts.findAll()) {
            try {
                tx.executeWithoutResult(s -> collectAccount(accounts.findById(account.getId()).orElseThrow()));
            } catch (PlatformApiException | DataIntegrityViolationException e) {
                log.warn("Skipping metrics for account {} this round: {}", account.getId(), e.getMessage());
            }
        }
        Instant now = Instant.now();
        for (PostPublication pub : publications.findPublishedSince(now.minus(POST_TRACKING_WINDOW))) {
            try {
                tx.executeWithoutResult(s ->
                        recordPostMetrics(publications.findById(pub.getId()).orElseThrow(), Instant.now(), false));
            } catch (PlatformApiException e) {
                log.warn("Skipping metrics for publication {}: {}", pub.getId(), e.getMessage());
            }
        }
    }

    /** Fills history for a newly connected account. Must run inside a transaction. */
    public void backfill(SocialAccount account, int days) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        for (LocalDate d = today.minusDays(days - 1L); !d.isAfter(today); d = d.plusDays(1)) {
            upsertSnapshot(account, d);
        }
        account.setLastSyncedAt(Instant.now());
    }

    /** Brings an account's snapshots up to date, filling any missed days. Must run inside a transaction. */
    public void collectAccount(SocialAccount account) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate from = snapshots.findTopBySocialAccountIdOrderBySnapshotDateDesc(account.getId())
                .map(AnalyticsSnapshot::getSnapshotDate)
                .filter(last -> !last.isBefore(today.minusDays(MAX_CATCH_UP_DAYS)))
                .orElse(today.minusDays(1));
        for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) {
            upsertSnapshot(account, d);
        }
        account.setLastSyncedAt(Instant.now());
    }

    /**
     * Refreshes a publication's latest metrics and appends a history point if the last one is old enough.
     * Must run inside a transaction.
     */
    public void recordPostMetrics(PostPublication pub, Instant at, boolean forceHistoryPoint) {
        if (pub.getExternalPostId() == null || pub.getPublishedAt() == null) {
            return;
        }
        PostMetrics m = clients.forAccount(pub.getSocialAccount())
                .fetchPostMetrics(pub.getSocialAccount(), pub.getExternalPostId(), pub.getPublishedAt());
        boolean addPoint = forceHistoryPoint || pub.getMetricsUpdatedAt() == null
                || Duration.between(pub.getMetricsUpdatedAt(), at).compareTo(POST_HISTORY_INTERVAL) >= 0;
        pub.updateMetrics(m.impressions(), m.likes(), m.comments(), m.shares(), at);
        if (addPoint) {
            postAnalytics.save(new PostAnalytics(pub, at, m.impressions(), m.likes(), m.comments(), m.shares()));
        }
    }

    /** Saves a series of history points for a publication; used to seed demo data. */
    public void recordPostHistory(PostPublication pub, List<Instant> points, List<PostMetrics> metrics) {
        for (int i = 0; i < points.size(); i++) {
            PostMetrics m = metrics.get(i);
            postAnalytics.save(new PostAnalytics(pub, points.get(i), m.impressions(), m.likes(), m.comments(),
                    m.shares()));
        }
    }

    private void upsertSnapshot(SocialAccount account, LocalDate date) {
        DailyMetrics m = clients.forAccount(account).fetchDailyMetrics(account, date);
        AnalyticsSnapshot snap = snapshots.findBySocialAccountIdAndSnapshotDate(account.getId(), date)
                .orElseGet(() -> new AnalyticsSnapshot(account, date));
        snap.update(m.followers(), m.impressions(), m.likes(), m.comments(), m.shares(), Instant.now());
        snapshots.save(snap);
    }
}
