package com.socialdash.post;

import com.socialdash.account.Platform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class PostDtos {

    private PostDtos() {}

    public enum PostAction { DRAFT, SCHEDULE, PUBLISH_NOW }

    public record PostRequest(
            @NotBlank @Size(max = 5000) String content,
            @Size(max = 1000) String mediaUrl,
            List<Long> accountIds,
            /** ISO-8601 instant, e.g. 2026-10-20T09:30:00Z. Clients convert from the user's local time. */
            Instant scheduledAt,
            @NotNull PostAction action) {}

    public record PublicationResponse(
            Long id,
            Long accountId,
            Platform platform,
            String accountHandle,
            PublicationStatus status,
            int attemptCount,
            Instant nextAttemptAt,
            Instant publishedAt,
            String externalUrl,
            String lastError,
            long impressions,
            long likes,
            long comments,
            long shares) {

        public static PublicationResponse from(PostPublication p) {
            var a = p.getSocialAccount();
            return new PublicationResponse(p.getId(), a.getId(), a.getPlatform(), a.getHandle(), p.getStatus(),
                    p.getAttemptCount(), p.getNextAttemptAt(), p.getPublishedAt(), p.getExternalUrl(),
                    p.getLastError(), p.getImpressions(), p.getLikes(), p.getComments(), p.getShares());
        }
    }

    public record PostResponse(
            Long id,
            String content,
            String mediaUrl,
            PostStatus status,
            boolean partiallyPublished,
            Instant scheduledAt,
            Instant createdAt,
            Instant updatedAt,
            List<PublicationResponse> publications) {

        public static PostResponse from(Post p) {
            List<PublicationResponse> pubs = p.getPublications().stream().map(PublicationResponse::from).toList();
            boolean partial = p.getStatus() == PostStatus.FAILED
                    && pubs.stream().anyMatch(x -> x.status() == PublicationStatus.PUBLISHED);
            return new PostResponse(p.getId(), p.getContent(), p.getMediaUrl(), p.getStatus(), partial,
                    p.getScheduledAt(), p.getCreatedAt(), p.getUpdatedAt(), pubs);
        }
    }

    public record PostSummary(Map<PostStatus, Long> counts) {}
}
