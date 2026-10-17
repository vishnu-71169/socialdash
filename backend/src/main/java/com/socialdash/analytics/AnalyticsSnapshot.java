package com.socialdash.analytics;

import com.socialdash.account.SocialAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/** One account's metrics for one UTC day. Engagement counts are for that day; followers is the end-of-day value. */
@Entity
@Table(name = "analytics_snapshots")
public class AnalyticsSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "social_account_id")
    private SocialAccount socialAccount;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    private long followers;
    private long impressions;
    private long likes;
    private long comments;
    private long shares;

    protected AnalyticsSnapshot() {}

    public AnalyticsSnapshot(SocialAccount socialAccount, LocalDate snapshotDate) {
        this.socialAccount = socialAccount;
        this.snapshotDate = snapshotDate;
    }

    public void update(long followers, long impressions, long likes, long comments, long shares, Instant at) {
        this.followers = followers;
        this.impressions = impressions;
        this.likes = likes;
        this.comments = comments;
        this.shares = shares;
        this.recordedAt = at;
    }

    public Long getId() { return id; }
    public SocialAccount getSocialAccount() { return socialAccount; }
    public LocalDate getSnapshotDate() { return snapshotDate; }
    public Instant getRecordedAt() { return recordedAt; }
    public long getFollowers() { return followers; }
    public long getImpressions() { return impressions; }
    public long getLikes() { return likes; }
    public long getComments() { return comments; }
    public long getShares() { return shares; }
}
