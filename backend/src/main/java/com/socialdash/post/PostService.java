package com.socialdash.post;

import com.socialdash.account.SocialAccount;
import com.socialdash.account.SocialAccountRepository;
import com.socialdash.common.ApiException;
import com.socialdash.common.PageResponse;
import com.socialdash.post.PostDtos.PostAction;
import com.socialdash.post.PostDtos.PostRequest;
import com.socialdash.post.PostDtos.PostResponse;
import com.socialdash.post.PostDtos.PostSummary;
import com.socialdash.user.UserService;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostService {

    /** Tolerate small clock differences between browser and server. */
    private static final Duration SCHEDULE_SKEW = Duration.ofMinutes(1);

    private final PostRepository posts;
    private final SocialAccountRepository accounts;
    private final UserService userService;

    public PostService(PostRepository posts, SocialAccountRepository accounts, UserService userService) {
        this.posts = posts;
        this.accounts = accounts;
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public PageResponse<PostResponse> list(Long userId, List<PostStatus> statuses, int page, int size) {
        Sort sort = statuses != null && statuses.size() == 1 && statuses.get(0) == PostStatus.SCHEDULED
                ? Sort.by("scheduledAt").ascending()
                : Sort.by("updatedAt").descending();
        PageRequest pageable = PageRequest.of(Math.max(page, 0), PageResponse.clampSize(size), sort);
        Page<Post> result = statuses == null || statuses.isEmpty()
                ? posts.findByUserId(userId, pageable)
                : posts.findByUserIdAndStatusIn(userId, statuses, pageable);
        return PageResponse.of(result, PostResponse::from);
    }

    @Transactional(readOnly = true)
    public PostSummary summary(Long userId) {
        Map<PostStatus, Long> counts = new EnumMap<>(PostStatus.class);
        for (PostStatus s : PostStatus.values()) counts.put(s, 0L);
        posts.countByStatus(userId).forEach(c -> counts.put(c.getStatus(), c.getTotal()));
        return new PostSummary(counts);
    }

    @Transactional(readOnly = true)
    public PostResponse get(Long userId, Long postId) {
        return PostResponse.from(owned(userId, postId));
    }

    @Transactional
    public PostResponse create(Long userId, PostRequest req) {
        Post post = new Post(userService.get(userId), req.content().trim(), blankToNull(req.mediaUrl()));
        List<SocialAccount> targets = resolveAccounts(userId, req.accountIds());
        validateForAction(req, targets);
        for (SocialAccount a : targets) {
            post.getPublications().add(new PostPublication(post, a, PublicationStatus.PENDING));
        }
        applyAction(post, req);
        return PostResponse.from(posts.save(post));
    }

    @Transactional
    public PostResponse update(Long userId, Long postId, PostRequest req) {
        Post post = owned(userId, postId);
        ensureEditable(post);
        List<SocialAccount> targets = resolveAccounts(userId, req.accountIds());
        validateForAction(req, targets);

        post.setContent(req.content().trim());
        post.setMediaUrl(blankToNull(req.mediaUrl()));

        // Diff rather than replace: removing and re-inserting the same account would violate the unique key.
        Set<Long> wanted = targets.stream().map(SocialAccount::getId).collect(Collectors.toSet());
        post.getPublications().removeIf(p -> !wanted.contains(p.getSocialAccount().getId()));
        Set<Long> existing = post.getPublications().stream()
                .map(p -> p.getSocialAccount().getId()).collect(Collectors.toCollection(HashSet::new));
        for (SocialAccount a : targets) {
            if (!existing.contains(a.getId())) {
                post.getPublications().add(new PostPublication(post, a, PublicationStatus.PENDING));
            }
        }
        applyAction(post, req);
        return PostResponse.from(post);
    }

    @Transactional
    public PostResponse cancel(Long userId, Long postId) {
        Post post = owned(userId, postId);
        if (post.getPublications().stream().anyMatch(p -> p.getStatus() == PublicationStatus.PUBLISHING)) {
            throw ApiException.conflict("This post is being published right now and can't be cancelled");
        }
        if (post.getStatus() == PostStatus.PUBLISHED || post.getStatus() == PostStatus.CANCELLED) {
            throw ApiException.conflict("Only drafts and scheduled posts can be cancelled");
        }
        for (PostPublication p : post.getPublications()) {
            if (p.getStatus() == PublicationStatus.SCHEDULED || p.getStatus() == PublicationStatus.PENDING) {
                p.setStatus(PublicationStatus.CANCELLED);
                p.setNextAttemptAt(null);
            }
        }
        if (post.getPublications().isEmpty()) {
            post.setStatus(PostStatus.CANCELLED);
            post.touch();
        } else {
            post.refreshStatus();
        }
        return PostResponse.from(post);
    }

    @Transactional
    public PostResponse retryFailed(Long userId, Long postId) {
        Post post = owned(userId, postId);
        List<PostPublication> failed = post.getPublications().stream()
                .filter(p -> p.getStatus() == PublicationStatus.FAILED).toList();
        if (failed.isEmpty()) {
            throw ApiException.badRequest("There are no failed publications to retry");
        }
        Instant now = Instant.now();
        failed.forEach(p -> p.schedule(now));
        post.refreshStatus();
        return PostResponse.from(post);
    }

    @Transactional
    public void delete(Long userId, Long postId) {
        Post post = owned(userId, postId);
        boolean delivered = post.getPublications().stream().anyMatch(p ->
                p.getStatus() == PublicationStatus.PUBLISHED || p.getStatus() == PublicationStatus.PUBLISHING);
        if (delivered) {
            throw ApiException.conflict("Published posts are kept for analytics and can't be deleted");
        }
        posts.delete(post);
    }

    // ---- helpers ----

    private Post owned(Long userId, Long postId) {
        return posts.findByIdAndUserId(postId, userId).orElseThrow(() -> ApiException.notFound("Post"));
    }

    private void ensureEditable(Post post) {
        boolean delivered = post.getPublications().stream().anyMatch(p ->
                p.getStatus() == PublicationStatus.PUBLISHED || p.getStatus() == PublicationStatus.PUBLISHING);
        if (delivered) {
            throw ApiException.conflict("Posts that have started publishing can't be edited");
        }
    }

    private List<SocialAccount> resolveAccounts(Long userId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Long> unique = List.copyOf(new LinkedHashSet<>(ids));
        List<SocialAccount> found = accounts.findByIdInAndUserId(unique, userId);
        if (found.size() != unique.size()) {
            throw ApiException.badRequest("One or more selected accounts don't exist");
        }
        return found;
    }

    private void validateForAction(PostRequest req, List<SocialAccount> targets) {
        if (req.action() != PostAction.DRAFT && targets.isEmpty()) {
            throw ApiException.badRequest("Select at least one account to publish to");
        }
        if (req.action() == PostAction.SCHEDULE) {
            if (req.scheduledAt() == null) {
                throw ApiException.badRequest("Choose a date and time to schedule this post");
            }
            if (req.scheduledAt().isBefore(Instant.now().minus(SCHEDULE_SKEW))) {
                throw ApiException.badRequest("Scheduled time must be in the future");
            }
        }
        String content = req.content().trim();
        for (SocialAccount a : targets) {
            int max = a.getPlatform().getMaxPostLength();
            if (content.length() > max) {
                throw ApiException.badRequest(a.getPlatform() + " posts are limited to " + max + " characters");
            }
        }
    }

    private void applyAction(Post post, PostRequest req) {
        switch (req.action()) {
            case DRAFT -> {
                post.setScheduledAt(req.scheduledAt());
                post.getPublications().forEach(p -> {
                    p.setStatus(PublicationStatus.PENDING);
                    p.setNextAttemptAt(null);
                    p.setAttemptCount(0);
                });
                post.setStatus(PostStatus.DRAFT);
            }
            case SCHEDULE, PUBLISH_NOW -> {
                Instant at = req.action() == PostAction.PUBLISH_NOW ? Instant.now() : req.scheduledAt();
                post.setScheduledAt(at);
                post.getPublications().forEach(p -> p.schedule(at));
                post.setStatus(PostStatus.SCHEDULED);
            }
        }
        post.refreshStatus();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
