package com.socialdash.analytics;

import com.socialdash.post.PostPublication;
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

/** Point-in-time metrics for a published post, used to chart how a post performed over time. */
@Entity
@Table(name = "post_analytics")
public class PostAnalytics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_publication_id")
    private PostPublication postPublication;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    private long impressions;
    private long likes;
    private long comments;
    private long shares;

    protected PostAnalytics() {}

    public PostAnalytics(PostPublication publication, Instant recordedAt, long impressions, long likes,
                         long comments, long shares) {
        this.postPublication = publication;
        this.recordedAt = recordedAt;
        this.impressions = impressions;
        this.likes = likes;
        this.comments = comments;
        this.shares = shares;
    }

    public Long getId() { return id; }
    public Instant getRecordedAt() { return recordedAt; }
    public long getImpressions() { return impressions; }
    public long getLikes() { return likes; }
    public long getComments() { return comments; }
    public long getShares() { return shares; }
}
