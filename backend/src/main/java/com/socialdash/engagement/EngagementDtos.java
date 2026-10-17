package com.socialdash.engagement;

import com.socialdash.account.Platform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;

public final class EngagementDtos {

    private EngagementDtos() {}

    public record ReplyRequest(@NotBlank @Size(max = 2000) String message) {}

    public record EngagementResponse(
            Long id,
            Long accountId,
            Platform platform,
            String accountHandle,
            Engagement.Type type,
            Engagement.Status status,
            String authorName,
            String authorHandle,
            String content,
            Instant receivedAt,
            String replyContent,
            Instant repliedAt,
            Instant resolvedAt) {

        public static EngagementResponse from(Engagement e) {
            var a = e.getSocialAccount();
            return new EngagementResponse(e.getId(), a.getId(), a.getPlatform(), a.getHandle(), e.getType(),
                    e.getStatus(), e.getAuthorName(), e.getAuthorHandle(), e.getContent(), e.getReceivedAt(),
                    e.getReplyContent(), e.getRepliedAt(), e.getResolvedAt());
        }
    }

    public record EngagementSummary(Map<Engagement.Status, Long> counts, Double avgResponseMinutes) {}
}
