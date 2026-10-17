package com.socialdash.engagement;

import com.socialdash.account.Platform;
import com.socialdash.account.SocialAccount;
import com.socialdash.common.ApiException;
import com.socialdash.common.PageResponse;
import com.socialdash.engagement.EngagementDtos.EngagementResponse;
import com.socialdash.engagement.EngagementDtos.EngagementSummary;
import com.socialdash.integration.PlatformApiException;
import com.socialdash.integration.PlatformClientRegistry;
import com.socialdash.integration.SocialPlatformClient.RawEngagement;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EngagementService {

    private static final Duration BACKFILL = Duration.ofDays(7);
    private static final Duration MAX_SYNC_WINDOW = Duration.ofDays(2);

    private final EngagementRepository engagements;
    private final PlatformClientRegistry clients;

    public EngagementService(EngagementRepository engagements, PlatformClientRegistry clients) {
        this.engagements = engagements;
        this.clients = clients;
    }

    public record Filter(Engagement.Status status, Engagement.Type type, Platform platform, Long accountId,
                         String q) {}

    @Transactional(readOnly = true)
    public PageResponse<EngagementResponse> list(Long userId, Filter f, int page, int size) {
        Specification<Engagement> spec = (root, query, cb) -> {
            var account = root.join("socialAccount", JoinType.INNER);
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(account.get("user").get("id"), userId));
            if (f.status() != null) predicates.add(cb.equal(root.get("status"), f.status()));
            if (f.type() != null) predicates.add(cb.equal(root.get("type"), f.type()));
            if (f.platform() != null) predicates.add(cb.equal(account.get("platform"), f.platform()));
            if (f.accountId() != null) predicates.add(cb.equal(account.get("id"), f.accountId()));
            if (f.q() != null && !f.q().isBlank()) {
                String like = "%" + f.q().trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("content")), like),
                        cb.like(cb.lower(root.get("authorName")), like),
                        cb.like(cb.lower(root.get("authorHandle")), like)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        var pageable = PageRequest.of(Math.max(page, 0), PageResponse.clampSize(size),
                Sort.by("receivedAt").descending());
        return PageResponse.of(engagements.findAll(spec, pageable), EngagementResponse::from);
    }

    @Transactional(readOnly = true)
    public EngagementSummary summary(Long userId) {
        Map<Engagement.Status, Long> counts = new EnumMap<>(Engagement.Status.class);
        for (Engagement.Status s : Engagement.Status.values()) counts.put(s, 0L);
        engagements.countByStatus(userId).forEach(c -> counts.put(c.getStatus(), c.getTotal()));
        List<Engagement> replied = engagements.findRepliedSince(userId, Instant.now().minus(Duration.ofDays(30)));
        Double avg = replied.isEmpty() ? null : Math.round(replied.stream()
                .mapToLong(e -> Duration.between(e.getReceivedAt(), e.getRepliedAt()).toMinutes())
                .average().orElse(0) * 10) / 10.0;
        return new EngagementSummary(counts, avg);
    }

    @Transactional
    public EngagementResponse reply(Long userId, Long id, String message) {
        Engagement e = owned(userId, id);
        try {
            clients.forAccount(e.getSocialAccount()).reply(e.getSocialAccount(), e.getExternalId(), message.trim());
        } catch (PlatformApiException ex) {
            throw new ApiException(ex.isRetryable() ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_REQUEST,
                    "Reply failed: " + ex.getMessage());
        }
        e.markReplied(message.trim(), Instant.now());
        return EngagementResponse.from(e);
    }

    @Transactional
    public EngagementResponse resolve(Long userId, Long id) {
        Engagement e = owned(userId, id);
        e.resolve(Instant.now());
        return EngagementResponse.from(e);
    }

    @Transactional
    public EngagementResponse reopen(Long userId, Long id) {
        Engagement e = owned(userId, id);
        e.reopen();
        return EngagementResponse.from(e);
    }

    /** Loads recent history for a newly connected account. Must run inside a transaction. */
    public void backfill(SocialAccount account) {
        Instant now = Instant.now();
        store(account, clients.forAccount(account).fetchEngagements(account, now.minus(BACKFILL), now));
        account.setEngagementsSyncedAt(now);
    }

    /** Fetches interactions since the last sync. Must run inside a transaction. */
    public void syncAccount(SocialAccount account) {
        Instant now = Instant.now();
        Instant since = account.getEngagementsSyncedAt() == null ? now.minus(BACKFILL)
                : account.getEngagementsSyncedAt();
        if (since.isBefore(now.minus(MAX_SYNC_WINDOW))) {
            since = now.minus(MAX_SYNC_WINDOW);
        }
        store(account, clients.forAccount(account).fetchEngagements(account, since, now));
        account.setEngagementsSyncedAt(now);
    }

    private void store(SocialAccount account, List<RawEngagement> raw) {
        for (RawEngagement r : raw) {
            if (engagements.existsBySocialAccountIdAndExternalId(account.getId(), r.externalId())) {
                continue; // platforms may return overlapping pages
            }
            engagements.save(new Engagement(account, r.externalId(), Engagement.Type.valueOf(r.type().name()),
                    r.authorName(), r.authorHandle(), r.content(), r.receivedAt()));
        }
    }

    private Engagement owned(Long userId, Long id) {
        return engagements.findOwned(id, userId).orElseThrow(() -> ApiException.notFound("Interaction"));
    }
}
