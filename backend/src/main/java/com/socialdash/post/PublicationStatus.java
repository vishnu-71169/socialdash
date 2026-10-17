package com.socialdash.post;

/** Delivery state of a post on one account. */
public enum PublicationStatus {
    /** Belongs to a draft; not queued. */
    PENDING,
    /** Queued; picked up once next_attempt_at has passed. Also used while waiting to retry. */
    SCHEDULED,
    /** Claimed by a scheduler run; the platform call is in flight. */
    PUBLISHING,
    PUBLISHED,
    /** Gave up after a non-retryable error or too many attempts. */
    FAILED,
    CANCELLED
}
