package com.socialdash.analytics;

import com.socialdash.account.Platform;
import java.time.Instant;
import java.time.LocalDate;

public final class AnalyticsDtos {

    private AnalyticsDtos() {}

    /** A value for the selected period, with the equivalent previous period for comparison. */
    public record Metric(double value, double previous, Double changePct) {
        public static Metric of(double value, double previous) {
            Double change = previous == 0 ? null : (value - previous) / Math.abs(previous) * 100.0;
            return new Metric(value, previous, change == null ? null : round(change));
        }
    }

    public record OverviewResponse(
            LocalDate startDate,
            LocalDate endDate,
            int accountCount,
            long publishedPosts,
            Metric followers,
            Metric followerGrowth,
            Metric impressions,
            Metric likes,
            Metric comments,
            Metric shares,
            /** (likes + comments + shares) / impressions, as a percentage. */
            Metric engagementRate) {}

    public record Breakdown(
            String key,
            Long accountId,
            Platform platform,
            String label,
            int accountCount,
            long followers,
            long followerGrowth,
            long impressions,
            long likes,
            long comments,
            long shares,
            double engagementRate) {}

    public record TrendPoint(
            LocalDate date,
            long followers,
            long impressions,
            long likes,
            long comments,
            long shares,
            long engagements,
            double engagementRate) {}

    public record PostPerformance(
            Long publicationId,
            Long postId,
            String content,
            Platform platform,
            String accountHandle,
            Instant publishedAt,
            String externalUrl,
            long impressions,
            long likes,
            long comments,
            long shares,
            long engagements,
            double engagementRate) {}

    public record PostHistoryPoint(Instant recordedAt, long impressions, long likes, long comments, long shares) {}

    static double engagementRate(long engagements, long impressions) {
        return impressions == 0 ? 0 : round(engagements * 100.0 / impressions);
    }

    static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
