package com.socialdash.analytics;

import static com.socialdash.analytics.AnalyticsDtos.engagementRate;

import com.socialdash.account.Platform;
import com.socialdash.account.SocialAccount;
import com.socialdash.account.SocialAccountRepository;
import com.socialdash.analytics.AnalyticsDtos.Breakdown;
import com.socialdash.analytics.AnalyticsDtos.Metric;
import com.socialdash.analytics.AnalyticsDtos.OverviewResponse;
import com.socialdash.analytics.AnalyticsDtos.PostHistoryPoint;
import com.socialdash.analytics.AnalyticsDtos.PostPerformance;
import com.socialdash.analytics.AnalyticsDtos.TrendPoint;
import com.socialdash.common.ApiException;
import com.socialdash.common.PageResponse;
import com.socialdash.post.PostPublication;
import com.socialdash.post.PostPublicationRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    private static final int DEFAULT_RANGE_DAYS = 30;
    private static final int MAX_RANGE_DAYS = 366;

    private final SocialAccountRepository accounts;
    private final AnalyticsSnapshotRepository snapshots;
    private final PostAnalyticsRepository postAnalytics;
    private final PostPublicationRepository publications;

    public AnalyticsService(SocialAccountRepository accounts, AnalyticsSnapshotRepository snapshots,
                            PostAnalyticsRepository postAnalytics, PostPublicationRepository publications) {
        this.accounts = accounts;
        this.snapshots = snapshots;
        this.postAnalytics = postAnalytics;
        this.publications = publications;
    }

    /** Query filters shared by all analytics endpoints. Null fields mean "all" / the default range. */
    public record Filter(Platform platform, Long accountId, LocalDate startDate, LocalDate endDate) {

        Filter resolved() {
            LocalDate end = endDate != null ? endDate : LocalDate.now(ZoneOffset.UTC);
            LocalDate start = startDate != null ? startDate : end.minusDays(DEFAULT_RANGE_DAYS - 1L);
            if (start.isAfter(end)) {
                throw ApiException.badRequest("startDate must be on or before endDate");
            }
            if (ChronoUnit.DAYS.between(start, end) >= MAX_RANGE_DAYS) {
                throw ApiException.badRequest("Date range can't exceed " + MAX_RANGE_DAYS + " days");
            }
            return new Filter(platform, accountId, start, end);
        }

        long days() {
            return ChronoUnit.DAYS.between(startDate, endDate) + 1;
        }

        Instant from() {
            return startDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        }

        Instant to() {
            return endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        }
    }

    private record Totals(long followersStart, long followersEnd, long impressions, long likes, long comments,
                          long shares) {
        long engagements() {
            return likes + comments + shares;
        }
    }

    public OverviewResponse overview(Long userId, Filter raw) {
        Filter f = raw.resolved();
        List<Long> ids = accountIds(userId, f);
        LocalDate prevEnd = f.startDate().minusDays(1);
        LocalDate prevStart = prevEnd.minusDays(f.days() - 1);

        Totals cur = totals(ids, f.startDate(), f.endDate());
        Totals prev = totals(ids, prevStart, prevEnd);
        long published = ids.isEmpty() ? 0 : publications.countPublished(ids, f.from(), f.to());

        return new OverviewResponse(f.startDate(), f.endDate(), ids.size(), published,
                Metric.of(cur.followersEnd(), prev.followersEnd()),
                Metric.of(cur.followersEnd() - cur.followersStart(), prev.followersEnd() - prev.followersStart()),
                Metric.of(cur.impressions(), prev.impressions()),
                Metric.of(cur.likes(), prev.likes()),
                Metric.of(cur.comments(), prev.comments()),
                Metric.of(cur.shares(), prev.shares()),
                Metric.of(engagementRate(cur.engagements(), cur.impressions()),
                        engagementRate(prev.engagements(), prev.impressions())));
    }

    public List<Breakdown> byPlatform(Long userId, Filter raw) {
        return breakdown(userId, raw.resolved(), a -> a.getPlatform().name(), true);
    }

    public List<Breakdown> byAccount(Long userId, Filter raw) {
        return breakdown(userId, raw.resolved(), a -> "account-" + a.getId(), false);
    }

    public List<TrendPoint> trends(Long userId, Filter raw) {
        Filter f = raw.resolved();
        List<Long> ids = accountIds(userId, f);
        if (ids.isEmpty()) return List.of();
        return snapshots.dailyTotals(ids, f.startDate(), f.endDate()).stream().map(d -> {
            long engagements = d.likes() + d.comments() + d.shares();
            return new TrendPoint(d.date(), d.followers(), d.impressions(), d.likes(), d.comments(), d.shares(),
                    engagements, engagementRate(engagements, d.impressions()));
        }).toList();
    }

    public PageResponse<PostPerformance> posts(Long userId, Filter raw, String sort, int page, int size) {
        Filter f = raw.resolved();
        List<Long> ids = accountIds(userId, f);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), PageResponse.clampSize(size));
        if (ids.isEmpty()) {
            return new PageResponse<>(List.of(), pageable.getPageNumber(), pageable.getPageSize(), 0, 0);
        }
        Page<PostPublication> result = "recent".equalsIgnoreCase(sort)
                ? publications.findRecentPublished(ids, f.from(), f.to(), pageable)
                : publications.findTopPublished(ids, f.from(), f.to(), pageable);
        return PageResponse.of(result, p -> new PostPerformance(p.getId(), p.getPost().getId(),
                p.getPost().getContent(), p.getSocialAccount().getPlatform(), p.getSocialAccount().getHandle(),
                p.getPublishedAt(), p.getExternalUrl(), p.getImpressions(), p.getLikes(), p.getComments(),
                p.getShares(), p.engagements(), engagementRate(p.engagements(), p.getImpressions())));
    }

    public List<PostHistoryPoint> postHistory(Long userId, Long publicationId) {
        publications.findOwned(publicationId, userId).orElseThrow(() -> ApiException.notFound("Publication"));
        return postAnalytics.findByPostPublicationIdOrderByRecordedAt(publicationId).stream()
                .map(a -> new PostHistoryPoint(a.getRecordedAt(), a.getImpressions(), a.getLikes(), a.getComments(),
                        a.getShares()))
                .toList();
    }

    // ---- helpers ----

    private List<SocialAccount> resolveAccounts(Long userId, Filter f) {
        if (f.accountId() != null) {
            SocialAccount a = accounts.findByIdAndUserId(f.accountId(), userId)
                    .orElseThrow(() -> ApiException.notFound("Account"));
            return f.platform() == null || a.getPlatform() == f.platform() ? List.of(a) : List.of();
        }
        return f.platform() != null
                ? accounts.findByUserIdAndPlatform(userId, f.platform())
                : accounts.findByUserIdOrderByConnectedAt(userId);
    }

    private List<Long> accountIds(Long userId, Filter f) {
        return resolveAccounts(userId, f).stream().map(SocialAccount::getId).toList();
    }

    private Totals totals(List<Long> ids, LocalDate start, LocalDate end) {
        if (ids.isEmpty()) return new Totals(0, 0, 0, 0, 0, 0);
        long followersStart = snapshots.firstInRange(ids, start, end).stream()
                .mapToLong(AnalyticsSnapshot::getFollowers).sum();
        long followersEnd = snapshots.lastInRange(ids, start, end).stream()
                .mapToLong(AnalyticsSnapshot::getFollowers).sum();
        long impressions = 0, likes = 0, comments = 0, shares = 0;
        for (AccountTotals t : snapshots.accountTotals(ids, start, end)) {
            impressions += t.impressions();
            likes += t.likes();
            comments += t.comments();
            shares += t.shares();
        }
        return new Totals(followersStart, followersEnd, impressions, likes, comments, shares);
    }

    private List<Breakdown> breakdown(Long userId, Filter f, Function<SocialAccount, String> keyFn,
                                      boolean byPlatform) {
        List<SocialAccount> list = resolveAccounts(userId, f);
        if (list.isEmpty()) return List.of();
        List<Long> ids = list.stream().map(SocialAccount::getId).toList();

        Map<Long, Long> first = new HashMap<>();
        snapshots.firstInRange(ids, f.startDate(), f.endDate())
                .forEach(s -> first.put(s.getSocialAccount().getId(), s.getFollowers()));
        Map<Long, Long> last = new HashMap<>();
        snapshots.lastInRange(ids, f.startDate(), f.endDate())
                .forEach(s -> last.put(s.getSocialAccount().getId(), s.getFollowers()));
        Map<Long, AccountTotals> totals = new HashMap<>();
        snapshots.accountTotals(ids, f.startDate(), f.endDate()).forEach(t -> totals.put(t.accountId(), t));

        Map<String, long[]> sums = new LinkedHashMap<>(); // accounts, followers, growth, impr, likes, comm, shares
        Map<String, SocialAccount> sample = new LinkedHashMap<>();
        for (SocialAccount a : list) {
            String key = keyFn.apply(a);
            sample.putIfAbsent(key, a);
            long[] s = sums.computeIfAbsent(key, k -> new long[7]);
            AccountTotals t = totals.get(a.getId());
            long f0 = first.getOrDefault(a.getId(), 0L);
            long f1 = last.getOrDefault(a.getId(), 0L);
            s[0] += 1;
            s[1] += f1;
            s[2] += f1 - f0;
            if (t != null) {
                s[3] += t.impressions();
                s[4] += t.likes();
                s[5] += t.comments();
                s[6] += t.shares();
            }
        }
        List<Breakdown> out = new ArrayList<>();
        sums.forEach((key, s) -> {
            SocialAccount a = sample.get(key);
            out.add(new Breakdown(key, byPlatform ? null : a.getId(), a.getPlatform(),
                    byPlatform ? a.getPlatform().name() : a.getHandle(),
                    (int) s[0], s[1], s[2], s[3], s[4], s[5], s[6], engagementRate(s[4] + s[5] + s[6], s[3])));
        });
        out.sort(Comparator.comparingLong(Breakdown::followers).reversed());
        return out;
    }
}
