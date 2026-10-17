package com.socialdash.account;

import com.socialdash.user.User;
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
@Table(name = "social_accounts")
public class SocialAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Platform platform;

    @Column(name = "platform_account_id", nullable = false)
    private String platformAccountId;

    @Column(nullable = false)
    private String handle;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(nullable = false)
    private boolean simulated;

    @Column(name = "access_token_encrypted")
    private String accessTokenEncrypted;

    @Column(name = "token_expires_at")
    private Instant tokenExpiresAt;

    @Column(name = "connected_at", nullable = false)
    private Instant connectedAt;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "engagements_synced_at")
    private Instant engagementsSyncedAt;

    protected SocialAccount() {}

    public SocialAccount(User user, Platform platform, String platformAccountId, String handle,
                         String displayName, boolean simulated) {
        this.user = user;
        this.platform = platform;
        this.platformAccountId = platformAccountId;
        this.handle = handle;
        this.displayName = displayName;
        this.simulated = simulated;
        this.connectedAt = Instant.now();
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public Platform getPlatform() { return platform; }
    public String getPlatformAccountId() { return platformAccountId; }
    public String getHandle() { return handle; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public boolean isSimulated() { return simulated; }
    public String getAccessTokenEncrypted() { return accessTokenEncrypted; }
    public void setAccessTokenEncrypted(String v) { this.accessTokenEncrypted = v; }
    public Instant getTokenExpiresAt() { return tokenExpiresAt; }
    public void setTokenExpiresAt(Instant tokenExpiresAt) { this.tokenExpiresAt = tokenExpiresAt; }
    public Instant getConnectedAt() { return connectedAt; }
    public Instant getLastSyncedAt() { return lastSyncedAt; }
    public void setLastSyncedAt(Instant lastSyncedAt) { this.lastSyncedAt = lastSyncedAt; }
    public Instant getEngagementsSyncedAt() { return engagementsSyncedAt; }
    public void setEngagementsSyncedAt(Instant v) { this.engagementsSyncedAt = v; }
}
