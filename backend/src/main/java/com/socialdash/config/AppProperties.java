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
        Simulation simulation) {

    public record Jwt(String secret, long expirationMinutes) {}

    public record Cors(List<String> allowedOrigins) {}

    public record Demo(boolean seed) {}

    public record Publishing(int maxAttempts, long retryBaseDelaySeconds, long stuckTimeoutSeconds, int batchSize) {}

    public record Simulation(double publishFailureRate) {}
}
