package com.socialdash.post;

import java.util.List;

/** Derives a post's overall status from the independent statuses of its per-account publications. */
public final class PostStatusCalculator {

    private PostStatusCalculator() {}

    public static PostStatus derive(PostStatus current, List<PostPublication> publications) {
        if (publications.isEmpty()) {
            return current == PostStatus.CANCELLED ? PostStatus.CANCELLED : PostStatus.DRAFT;
        }
        int pending = 0, scheduled = 0, publishing = 0, published = 0, failed = 0, cancelled = 0;
        boolean anyAttempted = false;
        for (PostPublication p : publications) {
            switch (p.getStatus()) {
                case PENDING -> pending++;
                case SCHEDULED -> scheduled++;
                case PUBLISHING -> publishing++;
                case PUBLISHED -> published++;
                case FAILED -> failed++;
                case CANCELLED -> cancelled++;
            }
            anyAttempted |= p.getAttemptCount() > 0;
        }
        int total = publications.size();

        if (published == total) return PostStatus.PUBLISHED;
        if (cancelled == total) return PostStatus.CANCELLED;
        if (pending == total) return PostStatus.DRAFT;
        if (publishing > 0) return PostStatus.PUBLISHING;
        if (scheduled > 0) {
            // Retrying, or some accounts already done: delivery is underway.
            return anyAttempted || published > 0 || failed > 0 ? PostStatus.PUBLISHING : PostStatus.SCHEDULED;
        }
        if (failed > 0) return PostStatus.FAILED;
        if (published > 0) return PostStatus.PUBLISHED; // the rest were cancelled
        return current;
    }
}
