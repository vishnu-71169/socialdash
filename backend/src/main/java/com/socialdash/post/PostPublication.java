package com.socialdash.post;

import com.socialdash.account.SocialAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "post_publications")
public class PostPublication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id")
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "social_account_id")
    private SocialAccount socialAccount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PublicationStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "external_post_id")
    private String externalPostId;

    @Column(name = "external_url")
    private String externalUrl;

    @Column(name = "last_error")
    private String lastError;

    private long impressions;
    private long likes;
    private long comments;
    private long shares;

    @Column(name = "metrics_updated_at")
    private Instant metricsUpdatedAt;

    @Version
    private long version;

    protected PostPublication() {}

    public PostPublication(Post post, SocialAccount socialAccount, PublicationStatus status) {
        this.post = post;
        this.socialAccount = socialAccount;
        this.status = status;
    }

    public void schedule(Instant at) {
        this.status = PublicationStatus.SCHEDULED;
        this.nextAttemptAt = at;
        this.attemptCount = 0;
        this.lastError = null;
    }

    public void markPublished(String externalPostId, String externalUrl, Instant at) {
        this.status = PublicationStatus.PUBLISHED;
        this.externalPostId = externalPostId;
        this.externalUrl = externalUrl;
        this.publishedAt = at;
        this.claimedAt = null;
        this.nextAttemptAt = null;
        this.lastError = null;
    }

    public void markRetry(String error, Instant nextAttempt) {
        this.status = PublicationStatus.SCHEDULED;
        this.lastError = truncate(error);
        this.nextAttemptAt = nextAttempt;
        this.claimedAt = null;
    }

    public void markFailed(String error) {
        this.status = PublicationStatus.FAILED;
        this.lastError = truncate(error);
        this.claimedAt = null;
        this.nextAttemptAt = null;
    }

    public void updateMetrics(long impressions, long likes, long comments, long shares, Instant at) {
        this.impressions = impressions;
        this.likes = likes;
        this.comments = comments;
        this.shares = shares;
        this.metricsUpdatedAt = at;
    }

    public long engagements() {
        return likes + comments + shares;
    }

    private static String truncate(String s) {
        return s == null || s.length() <= 1000 ? s : s.substring(0, 1000);
    }

    public Long getId() { return id; }
    public Post getPost() { return post; }
    public SocialAccount getSocialAccount() { return socialAccount; }
    public PublicationStatus getStatus() { return status; }
    public void setStatus(PublicationStatus status) { this.status = status; }
    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(Instant nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }
    public Instant getClaimedAt() { return claimedAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public String getExternalPostId() { return externalPostId; }
    public String getExternalUrl() { return externalUrl; }
    public String getLastError() { return lastError; }
    public long getImpressions() { return impressions; }
    public long getLikes() { return likes; }
    public long getComments() { return comments; }
    public long getShares() { return shares; }
    public Instant getMetricsUpdatedAt() { return metricsUpdatedAt; }
}
