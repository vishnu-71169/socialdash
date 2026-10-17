package com.socialdash.listening;

import com.socialdash.account.Platform;
import com.socialdash.common.ApiException;
import com.socialdash.common.PageResponse;
import com.socialdash.integration.PlatformClientRegistry;
import com.socialdash.integration.SocialPlatformClient.RawMention;
import com.socialdash.listening.ListeningDtos.AddKeywordRequest;
import com.socialdash.listening.ListeningDtos.KeywordResponse;
import com.socialdash.listening.ListeningDtos.MentionResponse;
import com.socialdash.listening.ListeningDtos.TermCount;
import com.socialdash.listening.ListeningDtos.VolumeResponse;
import com.socialdash.listening.ListeningDtos.VolumeSeries;
import com.socialdash.user.UserService;
import jakarta.persistence.criteria.Predicate;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListeningService {

    private static final int MAX_KEYWORDS = 20;
    private static final Duration BACKFILL = Duration.ofDays(30);
    private static final Duration MAX_POLL_WINDOW = Duration.ofDays(1);
    private static final Set<String> STOPWORDS = Set.of(
            "the", "a", "an", "and", "or", "but", "is", "are", "was", "it", "its", "to", "of", "in", "on", "for",
            "with", "by", "at", "this", "that", "so", "far", "just", "i", "my", "our", "me", "you", "your", "any",
            "can", "someone", "anyone", "else", "has", "have", "be", "been", "as", "up", "out", "how", "what", "where",
            "if", "not", "do", "does", "about", "from", "last", "keep", "eye", "change", "mind", "asking",
            "friend", "today", "feels", "honestly", "team", "worth", "who", "than", "too", "very", "all", "big",
            "something", "announced", "explain", "changes", "take", "hot", "having", "issues", "thoughts",
            "month", "tried", "loving", "great", "thread", "heading", "industry");

    private final TrackedKeywordRepository keywords;
    private final SocialMentionRepository mentions;
    private final PlatformClientRegistry clients;
    private final UserService userService;

    public ListeningService(TrackedKeywordRepository keywords, SocialMentionRepository mentions,
                            PlatformClientRegistry clients, UserService userService) {
        this.keywords = keywords;
        this.mentions = mentions;
        this.clients = clients;
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public List<KeywordResponse> keywords(Long userId) {
        List<TrackedKeyword> list = keywords.findByUserIdOrderByCreatedAt(userId);
        if (list.isEmpty()) return List.of();
        List<Long> ids = list.stream().map(TrackedKeyword::getId).toList();
        Instant now = Instant.now();
        Map<Long, Long> day = toMap(mentions.countSince(ids, now.minus(Duration.ofDays(1))));
        Map<Long, Long> week = toMap(mentions.countSince(ids, now.minus(Duration.ofDays(7))));
        return list.stream().map(k -> new KeywordResponse(k.getId(), k.getKeyword(), k.getPlatform(),
                k.getCreatedAt(), day.getOrDefault(k.getId(), 0L), week.getOrDefault(k.getId(), 0L))).toList();
    }

    @Transactional
    public KeywordResponse addKeyword(Long userId, AddKeywordRequest req) {
        String keyword = req.keyword().trim().replaceAll("\\s+", " ");
        boolean duplicate = keywords.findByUserIdAndKeywordIgnoreCase(userId, keyword).stream()
                .anyMatch(k -> Objects.equals(k.getPlatform(), req.platform()));
        if (duplicate) {
            throw ApiException.conflict("You're already tracking \"" + keyword + "\"");
        }
        if (keywords.findByUserIdOrderByCreatedAt(userId).size() >= MAX_KEYWORDS) {
            throw ApiException.badRequest("You can track up to " + MAX_KEYWORDS + " keywords");
        }
        TrackedKeyword k = keywords.save(new TrackedKeyword(userService.get(userId), keyword, req.platform()));
        Instant now = Instant.now();
        poll(k, now.minus(BACKFILL), now);
        return keywords(userId).stream().filter(r -> r.id().equals(k.getId())).findFirst().orElseThrow();
    }

    @Transactional
    public void deleteKeyword(Long userId, Long keywordId) {
        keywords.delete(keywords.findByIdAndUserId(keywordId, userId)
                .orElseThrow(() -> ApiException.notFound("Keyword")));
    }

    @Transactional(readOnly = true)
    public PageResponse<MentionResponse> mentions(Long userId, Long keywordId, Platform platform, String q,
                                                  int page, int size) {
        Specification<SocialMention> spec = (root, query, cb) -> {
            var keyword = root.join("trackedKeyword");
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(keyword.get("user").get("id"), userId));
            if (keywordId != null) p.add(cb.equal(keyword.get("id"), keywordId));
            if (platform != null) p.add(cb.equal(root.get("platform"), platform));
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("content")), like),
                        cb.like(cb.lower(root.get("authorHandle")), like)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        var pageable = PageRequest.of(Math.max(page, 0), PageResponse.clampSize(size),
                Sort.by("detectedAt").descending());
        return PageResponse.of(mentions.findAll(spec, pageable), MentionResponse::from);
    }

    /** Daily mention counts per keyword (UTC days) over the last {@code days}, with the previous period's total. */
    @Transactional(readOnly = true)
    public VolumeResponse volume(Long userId, Long keywordId, int days) {
        int span = Math.max(1, Math.min(days, 90));
        List<TrackedKeyword> list = selected(userId, keywordId);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate start = today.minusDays(span - 1L);
        List<LocalDate> dates = start.datesUntil(today.plusDays(1)).toList();
        if (list.isEmpty()) return new VolumeResponse(dates, List.of());

        List<Long> ids = list.stream().map(TrackedKeyword::getId).toList();
        Instant from = start.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant prevFrom = start.minusDays(span).atStartOfDay(ZoneOffset.UTC).toInstant();

        Map<Long, long[]> buckets = new HashMap<>();
        ids.forEach(id -> buckets.put(id, new long[span]));
        for (var t : mentions.findTimes(ids, from, to)) {
            int idx = (int) (t.getDetectedAt().atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() - start.toEpochDay());
            if (idx >= 0 && idx < span) buckets.get(t.getKeywordId())[idx]++;
        }
        Map<Long, Long> previous = new HashMap<>();
        for (var t : mentions.findTimes(ids, prevFrom, from)) previous.merge(t.getKeywordId(), 1L, Long::sum);
        // Only compare against the previous period if we were already collecting for all of it.
        Instant coveredFrom = list.stream().map(k -> k.getCreatedAt().minus(BACKFILL)).max(Instant::compareTo).orElseThrow();
        boolean prevCovered = !prevFrom.isBefore(coveredFrom);

        List<VolumeSeries> series = list.stream().map(k -> {
            long[] c = buckets.get(k.getId());
            return new VolumeSeries(k.getId(), k.getKeyword(), Arrays.stream(c).boxed().toList(),
                    Arrays.stream(c).sum(), prevCovered ? previous.getOrDefault(k.getId(), 0L) : null);
        }).toList();
        return new VolumeResponse(dates, series);
    }

    /** Most frequent words and hashtags appearing alongside tracked keywords. */
    @Transactional(readOnly = true)
    public List<TermCount> topTerms(Long userId, Long keywordId, int days) {
        List<TrackedKeyword> list = selected(userId, keywordId);
        if (list.isEmpty()) return List.of();
        Set<String> exclude = list.stream()
                .flatMap(k -> Arrays.stream(k.getKeyword().toLowerCase(Locale.ROOT).replace("#", "").split(" ")))
                .collect(Collectors.toSet());
        Instant to = Instant.now();
        Instant from = to.minus(Duration.ofDays(Math.max(1, Math.min(days, 90))));
        Map<String, Long> freq = new HashMap<>();
        for (String content : mentions.findContents(list.stream().map(TrackedKeyword::getId).toList(), from, to)) {
            for (String raw : content.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}#_]+")) {
                String term = raw.replaceAll("^#+", "#");
                String bare = term.replace("#", "");
                if (bare.length() < 3 || STOPWORDS.contains(bare) || exclude.contains(bare)) continue;
                freq.merge(term, 1L, Long::sum);
            }
        }
        return freq.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(15)
                .map(e -> new TermCount(e.getKey(), e.getValue()))
                .toList();
    }

    /** Fetches and stores new mentions for a keyword. Must run inside a transaction. */
    public int poll(TrackedKeyword k, Instant since, Instant until) {
        if (since.isBefore(until.minus(BACKFILL))) since = until.minus(BACKFILL);
        int saved = 0;
        for (RawMention r : clients.forListening().searchMentions(k.getKeyword(), k.getPlatform(), since, until)) {
            if (mentions.existsByTrackedKeywordIdAndPlatformAndExternalId(k.getId(), r.platform(), r.externalId())) {
                continue;
            }
            mentions.save(new SocialMention(k, r.platform(), r.externalId(), r.authorHandle(), r.content(), r.url(),
                    r.detectedAt()));
            saved++;
        }
        k.setLastCheckedAt(until);
        return saved;
    }

    /** Poll window for the scheduled collector: since the last check, capped so a long outage stays bounded. */
    public void pollSinceLastCheck(TrackedKeyword k) {
        Instant now = Instant.now();
        Instant since = k.getLastCheckedAt() == null ? now.minus(BACKFILL) : k.getLastCheckedAt();
        if (since.isBefore(now.minus(MAX_POLL_WINDOW))) since = now.minus(MAX_POLL_WINDOW);
        poll(k, since, now);
    }

    private List<TrackedKeyword> selected(Long userId, Long keywordId) {
        if (keywordId == null) return keywords.findByUserIdOrderByCreatedAt(userId);
        return List.of(keywords.findByIdAndUserId(keywordId, userId)
                .orElseThrow(() -> ApiException.notFound("Keyword")));
    }

    private static Map<Long, Long> toMap(List<SocialMentionRepository.KeywordCount> counts) {
        return counts.stream().collect(Collectors.toMap(SocialMentionRepository.KeywordCount::getKeywordId,
                SocialMentionRepository.KeywordCount::getTotal));
    }
}
