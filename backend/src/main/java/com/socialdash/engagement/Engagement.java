package com.socialdash.engagement;

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
import java.time.Instant;

/** An audience interaction (comment, mention or DM) that may need a response. */
@Entity
@Table(name = "engagements")
public class Engagement {

    public enum Type { COMMENT, MENTION, DIRECT_MESSAGE }

    public enum Status { OPEN, REPLIED, RESOLVED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "social_account_id")
    private SocialAccount socialAccount;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.OPEN;

    @Column(name = "author_name", nullable = false)
    private String authorName;

    @Column(name = "author_handle", nullable = false)
    private String authorHandle;

    @Column(nullable = false)
    private String content;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "reply_content")
    private String replyContent;

    @Column(name = "replied_at")
    private Instant repliedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected Engagement() {}

    public Engagement(SocialAccount account, String externalId, Type type, String authorName, String authorHandle,
                      String content, Instant receivedAt) {
        this.socialAccount = account;
        this.externalId = externalId;
        this.type = type;
        this.authorName = authorName;
        this.authorHandle = authorHandle;
        this.content = content;
        this.receivedAt = receivedAt;
    }

    public void markReplied(String reply, Instant at) {
        this.replyContent = reply;
        this.repliedAt = at;
        this.status = Status.REPLIED;
    }

    public void resolve(Instant at) {
        this.status = Status.RESOLVED;
        this.resolvedAt = at;
    }

    public void reopen() {
        this.status = replyContent != null ? Status.REPLIED : Status.OPEN;
        this.resolvedAt = null;
    }

    public Long getId() { return id; }
    public SocialAccount getSocialAccount() { return socialAccount; }
    public String getExternalId() { return externalId; }
    public Type getType() { return type; }
    public Status getStatus() { return status; }
    public String getAuthorName() { return authorName; }
    public String getAuthorHandle() { return authorHandle; }
    public String getContent() { return content; }
    public Instant getReceivedAt() { return receivedAt; }
    public String getReplyContent() { return replyContent; }
    public Instant getRepliedAt() { return repliedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
}
