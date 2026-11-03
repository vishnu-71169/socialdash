package com.socialdash.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        String tokenEncryptionKey,
        Cors cors,
        Demo demo,
        Publishing publishing,
        Simulation simulation,
        Meta meta) {

    public record Jwt(String secret, long expirationMinutes) {}

    public record Cors(List<String> allowedOrigins) {}

    public record Demo(boolean seed) {}

    public record Publishing(int maxAttempts, long retryBaseDelaySeconds, long stuckTimeoutSeconds, int batchSize) {}

    public record Simulation(double publishFailureRate) {}

    /**
     * Meta Graph API (Instagram + Facebook Pages). App id/secret are optional: when set, Facebook tokens are exchanged
     * for long-lived ones on connect. Metric names are configurable because Meta renames and retires insights metrics.
     */
    public record Meta(
            String baseUrl,
            String apiVersion,
            String appId,
            String appSecret,
            int syncIntervalMinutes,
            int backfillDays,
            String instagramViewsMetric,
            String facebookViewsMetric,
            String facebookReactionsMetric,
            String facebookPostViewsMetric) {

        public boolean canExchangeTokens() {
            return appId != null && !appId.isBlank() && appSecret != null && !appSecret.isBlank();
        }
    }
}
