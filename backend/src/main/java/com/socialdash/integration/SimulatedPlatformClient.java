package com.socialdash.integration;

import com.socialdash.account.Platform;
import com.socialdash.account.SocialAccount;
import com.socialdash.config.AppProperties;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

/**
 * Generates plausible, deterministic data for simulated accounts so the whole application can be exercised without
 * platform API approval. Account metrics are a pure function of (account, date), which keeps history stable across
 * restarts; publishing randomly fails at a configurable rate to exercise retry handling.
 */
@Component
public class SimulatedPlatformClient implements SocialPlatformClient {

    private static final LocalDate EPOCH = LocalDate.of(2024, 1, 1);

    private static final String[] MENTION_TEMPLATES = {
            "Just tried %s and honestly loving it so far!",
            "Anyone else having issues with %s today?",
            "%s just announced something big, keep an eye on this",
            "Comparing %s with the alternatives - thoughts?",
            "Big shoutout to the %s team for the quick support",
            "Not impressed by the latest %s update, feels slower",
            "Our team switched to %s last month. Productivity is up",
            "Is %s worth it for small businesses? Asking for a friend",
            "Great thread on %s and where the industry is heading",
            "%s pricing changes are confusing, can someone explain?",
            "Loved the %s webinar this morning, super practical tips",
            "Hot take: %s is overrated. Change my mind",
    };

    private static final String[] COMMENT_TEMPLATES = {
            "This is so helpful, thanks for sharing!",
            "When will this be available in my country?",
            "Love this! Can you do a follow-up post?",
            "I tried this and it didn't work for me. Any tips?",
            "Great insights as always",
            "Do you have a link to the full guide?",
            "Totally agree with point #2",
            "How does this compare to last year?",
    };

    private static final String[] MESSAGE_TEMPLATES = {
            "Hi! I'd love to collaborate on a campaign. Who should I contact?",
            "My order hasn't arrived yet, can you help?",
            "Are you hiring for marketing roles right now?",
            "Can I get a discount code for your next launch?",
    };

    private static final String[] AUTHOR_NAMES = {
            "Priya Sharma", "Alex Chen", "Maria Garcia", "Jordan Lee", "Sam Patel", "Taylor Kim",
            "Chris Morgan", "Aisha Khan", "Diego Rossi", "Mei Tanaka", "Noah Williams", "Fatima Ali",
    };

    private final double publishFailureRate;
    /** Remembers results by idempotency key, like a real platform's idempotent-create API would. */
    private final Map<String, PublishResult> publishedByKey = new ConcurrentHashMap<>();

    public SimulatedPlatformClient(AppProperties props) {
        this.publishFailureRate = props.simulation().publishFailureRate();
    }

    @Override
    public DailyMetrics fetchDailyMetrics(SocialAccount account, LocalDate date) {
        Profile p = profile(account);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        if (date.isAfter(today)) {
            return new DailyMetrics(followersOn(p, today), 0, 0, 0, 0);
        }
        double fraction = date.equals(today) ? dayFractionElapsed() : 1.0;

        long followersStart = followersOn(p, date.minusDays(1));
        long followersEnd = followersOn(p, date);
        long followers = followersStart + Math.round((followersEnd - followersStart) * fraction);

        Random rd = new Random(p.seed * 31 + date.toEpochDay());
        boolean weekend = date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
        double reach = (0.12 + rd.nextDouble() * 0.22) * (weekend ? p.weekendFactor : 1.0);
        if (rd.nextDouble() < 0.06) {
            reach *= 2.5 + rd.nextDouble() * 2; // the occasional viral day
        }
        long impressions = Math.round(followers * reach * fraction);
        long likes = Math.round(impressions * p.likeRate * (0.75 + rd.nextDouble() * 0.5));
        long comments = Math.round(likes * (0.04 + rd.nextDouble() * 0.10));
        long shares = Math.round(likes * p.shareRatio * (0.6 + rd.nextDouble() * 0.8));
        return new DailyMetrics(followers, impressions, likes, comments, shares);
    }

    @Override
    public PublishResult publish(SocialAccount account, String content, String idempotencyKey) {
        if (content.length() > account.getPlatform().getMaxPostLength()) {
            throw new PlatformApiException("Content exceeds " + account.getPlatform() + " limit of "
                    + account.getPlatform().getMaxPostLength() + " characters", false);
        }
        PublishResult existing = publishedByKey.get(idempotencyKey);
        if (existing != null) {
            return existing;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (r.nextDouble() < publishFailureRate) {
            if (r.nextBoolean()) {
                // Ambiguous failure: the platform accepted the post but the response never arrived.
                publishedByKey.computeIfAbsent(idempotencyKey, k -> newResult(account));
                throw new PlatformApiException("Simulated timeout waiting for " + account.getPlatform() + " API", true);
            }
            throw new PlatformApiException("Simulated " + account.getPlatform() + " API rate limit (HTTP 429)", true);
        }
        return publishedByKey.computeIfAbsent(idempotencyKey, k -> newResult(account));
    }

    @Override
    public PostMetrics fetchPostMetrics(SocialAccount account, String externalPostId, Instant publishedAt) {
        return postMetricsAt(account, externalPostId, publishedAt, Instant.now());
    }

    /** Post metrics as they would have been at {@code asOf}; lets the demo seeder build realistic history. */
    public PostMetrics postMetricsAt(SocialAccount account, String externalPostId, Instant publishedAt,
                                     Instant asOf) {
        Profile p = profile(account);
        Random r = new Random(externalPostId.hashCode());
        double hours = Math.max(0, Duration.between(publishedAt, asOf).toMinutes() / 60.0);
        double curve = 1 - Math.exp(-hours / 14.0);
        long peakImpressions = Math.round(followersOn(p, LocalDate.now(ZoneOffset.UTC)) * (0.08 + r.nextDouble() * 0.5));
        long impressions = Math.round(peakImpressions * curve);
        long likes = Math.round(impressions * p.likeRate * (0.6 + r.nextDouble() * 0.9));
        long comments = Math.round(likes * (0.05 + r.nextDouble() * 0.1));
        long shares = Math.round(likes * p.shareRatio * (0.5 + r.nextDouble()));
        return new PostMetrics(impressions, likes, comments, shares);
    }

    @Override
    public List<RawMention> searchMentions(String keyword, Platform platform, Instant since, Instant until) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        long minutes = Math.max(0, Duration.between(since, until).toMinutes());
        // ~1 mention per 40 minutes on average with some burstiness, capped per call
        int count = (int) Math.min(1500, Math.round(minutes / 40.0 * (0.4 + r.nextDouble() * 1.2)));
        List<RawMention> mentions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Platform pf = platform != null ? platform : Platform.values()[r.nextInt(Platform.values().length)];
            String text = String.format(MENTION_TEMPLATES[r.nextInt(MENTION_TEMPLATES.length)], keyword);
            if (r.nextDouble() < 0.3) {
                text += " #" + pick(r, "marketing", "socialmedia", "growth", "startup", "branding", "contentcreator");
            }
            String handle = handleFor(pick(r, AUTHOR_NAMES), r);
            String id = UUID.randomUUID().toString();
            Instant at = since.plusSeconds(r.nextLong(Math.max(1, minutes * 60)));
            mentions.add(new RawMention(pf, id, handle, text, postUrl(pf, handle, id), at));
        }
        return mentions;
    }

    @Override
    public List<RawEngagement> fetchEngagements(SocialAccount account, Instant since, Instant until) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        long minutes = Math.max(0, Duration.between(since, until).toMinutes());
        int count = (int) Math.min(60, Math.round(minutes / 90.0 * (0.3 + r.nextDouble() * 1.4)));
        List<RawEngagement> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double roll = r.nextDouble();
            RawEngagementType type = roll < 0.6 ? RawEngagementType.COMMENT
                    : roll < 0.85 ? RawEngagementType.MENTION : RawEngagementType.DIRECT_MESSAGE;
            String name = pick(r, AUTHOR_NAMES);
            String content = switch (type) {
                case COMMENT -> pick(r, COMMENT_TEMPLATES);
                case MENTION -> "Hey @" + account.getHandle().replace("@", "") + " " + pick(r, COMMENT_TEMPLATES).toLowerCase(Locale.ROOT);
                case DIRECT_MESSAGE -> pick(r, MESSAGE_TEMPLATES);
            };
            Instant at = since.plusSeconds(r.nextLong(Math.max(1, minutes * 60)));
            result.add(new RawEngagement(UUID.randomUUID().toString(), type, name, handleFor(name, r), content, at));
        }
        return result;
    }

    @Override
    public void reply(SocialAccount account, String engagementExternalId, String message) {
        if (message.length() > account.getPlatform().getMaxPostLength()) {
            throw new PlatformApiException("Reply exceeds " + account.getPlatform() + " character limit", false);
        }
        // Simulated: accepted.
    }

    // ---- helpers ----

    private record Profile(long seed, long baseFollowers, double dailyGrowth, double likeRate, double shareRatio,
                           double weekendFactor) {}

    private Profile profile(SocialAccount account) {
        long seed = account.getPlatformAccountId().hashCode();
        Random r = new Random(seed);
        double platformScale = switch (account.getPlatform()) {
            case INSTAGRAM -> 1.6;
            case TWITTER -> 1.2;
            case FACEBOOK -> 1.0;
            case LINKEDIN -> 0.5;
        };
        double likeRate = switch (account.getPlatform()) {
            case INSTAGRAM -> 0.06;
            case LINKEDIN -> 0.035;
            case FACEBOOK -> 0.03;
            case TWITTER -> 0.025;
        };
        double shareRatio = account.getPlatform() == Platform.INSTAGRAM ? 0.05 : 0.15;
        return new Profile(seed,
                Math.round((1_500 + r.nextInt(25_000)) * platformScale),
                (2 + r.nextDouble() * 30) * platformScale,
                likeRate * (0.7 + r.nextDouble() * 0.6),
                shareRatio,
                account.getPlatform() == Platform.LINKEDIN ? 0.45 : 0.85 + r.nextDouble() * 0.3);
    }

    private long followersOn(Profile p, LocalDate date) {
        long day = date.toEpochDay() - EPOCH.toEpochDay();
        double wobble = p.dailyGrowth * 6 * Math.sin(day / 11.0) + p.dailyGrowth * 2 * Math.sin(day / 3.3);
        return Math.max(0, Math.round(p.baseFollowers + p.dailyGrowth * day + wobble));
    }

    private static double dayFractionElapsed() {
        Instant now = Instant.now();
        Instant startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        return Math.min(1.0, Duration.between(startOfDay, now).getSeconds() / 86_400.0);
    }

    private static PublishResult newResult(SocialAccount account) {
        String id = account.getPlatform().name().toLowerCase(Locale.ROOT) + "_" + UUID.randomUUID();
        return new PublishResult(id, postUrl(account.getPlatform(), account.getHandle(), id));
    }

    private static String postUrl(Platform platform, String handle, String id) {
        return "https://sim." + platform.name().toLowerCase(Locale.ROOT) + ".example/"
                + handle.replace("@", "") + "/posts/" + id;
    }

    private static String handleFor(String name, ThreadLocalRandom r) {
        return "@" + name.toLowerCase(Locale.ROOT).replace(' ', '_') + (r.nextInt(90) + 10);
    }

    @SafeVarargs
    private static <T> T pick(ThreadLocalRandom r, T... options) {
        return options[r.nextInt(options.length)];
    }
}
