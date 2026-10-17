package com.socialdash.listening;

import com.socialdash.account.Platform;
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
import java.time.Instant;

@Entity
@Table(name = "social_mentions")
public class SocialMention {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tracked_keyword_id")
    private TrackedKeyword trackedKeyword;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Platform platform;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Column(name = "author_handle", nullable = false)
    private String authorHandle;

    @Column(nullable = false)
    private String content;

    private String url;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    protected SocialMention() {}

    public SocialMention(TrackedKeyword keyword, Platform platform, String externalId, String authorHandle,
                         String content, String url, Instant detectedAt) {
        this.trackedKeyword = keyword;
        this.platform = platform;
        this.externalId = externalId;
        this.authorHandle = authorHandle;
        this.content = content;
        this.url = url;
        this.detectedAt = detectedAt;
    }

    public Long getId() { return id; }
    public TrackedKeyword getTrackedKeyword() { return trackedKeyword; }
    public Platform getPlatform() { return platform; }
    public String getExternalId() { return externalId; }
    public String getAuthorHandle() { return authorHandle; }
    public String getContent() { return content; }
    public String getUrl() { return url; }
    public Instant getDetectedAt() { return detectedAt; }
}
