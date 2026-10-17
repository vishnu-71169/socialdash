package com.socialdash.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.socialdash.IntegrationTestBase;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

class PublishingIntegrationTest extends IntegrationTestBase {

    @Autowired
    PublishingScheduler scheduler;

    @Autowired
    PostPublicationRepository publications;

    @Autowired
    TransactionTemplate tx;

    @Test
    void publishNowIsDeliveredToEveryAccountByTheScheduler() throws Exception {
        String token = registerUser();
        long ig = connectAccount(token, "INSTAGRAM", "brand");
        long li = connectAccount(token, "LINKEDIN", "brand_co");

        JsonNode created = perform(post("/api/posts").content("""
                {"content":"Hello world","accountIds":[%d,%d],"action":"PUBLISH_NOW"}""".formatted(ig, li)),
                token, 201);
        assertThat(created.get("status").asText()).isEqualTo("SCHEDULED");

        scheduler.run();

        JsonNode post = perform(get("/api/posts/" + created.get("id").asLong()), token, 200);
        assertThat(post.get("status").asText()).isEqualTo("PUBLISHED");
        assertThat(post.get("publications")).allSatisfy(p -> {
            assertThat(p.get("status").asText()).isEqualTo("PUBLISHED");
            assertThat(p.get("externalUrl").asText()).isNotBlank();
        });
    }

    @Test
    void aPublicationCanOnlyBeClaimedOnce() throws Exception {
        String token = registerUser();
        long ig = connectAccount(token, "INSTAGRAM", "claimtest");
        JsonNode created = perform(post("/api/posts").content("""
                {"content":"Claim me","accountIds":[%d],"action":"PUBLISH_NOW"}""".formatted(ig)), token, 201);
        long pubId = created.get("publications").get(0).get("id").asLong();

        Instant now = Instant.now().plus(1, ChronoUnit.SECONDS);
        Integer first = tx.execute(s -> publications.claim(pubId, now));
        Integer second = tx.execute(s -> publications.claim(pubId, now));
        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
    }

    @Test
    void contentOverPlatformLimitIsRejectedUpFront() throws Exception {
        String token = registerUser();
        long tw = connectAccount(token, "TWITTER", "short");
        String longText = "x".repeat(281);
        perform(post("/api/posts").content("""
                {"content":"%s","accountIds":[%d],"action":"PUBLISH_NOW"}""".formatted(longText, tw)), token, 400);
    }

    @Test
    void scheduledPostsCanBeCancelledButNotInThePast() throws Exception {
        String token = registerUser();
        long fb = connectAccount(token, "FACEBOOK", "page");
        perform(post("/api/posts").content("""
                {"content":"Too late","accountIds":[%d],"action":"SCHEDULE","scheduledAt":"2020-01-01T00:00:00Z"}"""
                .formatted(fb)), token, 400);

        String future = Instant.now().plus(2, ChronoUnit.DAYS).toString();
        JsonNode created = perform(post("/api/posts").content("""
                {"content":"Later","accountIds":[%d],"action":"SCHEDULE","scheduledAt":"%s"}"""
                .formatted(fb, future)), token, 201);
        JsonNode cancelled = perform(post("/api/posts/" + created.get("id").asLong() + "/cancel"), token, 200);
        assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");

        scheduler.run(); // nothing due; the cancelled publication must not be picked up
        JsonNode after = perform(get("/api/posts/" + created.get("id").asLong()), token, 200);
        assertThat(after.get("publications").get(0).get("status").asText()).isEqualTo("CANCELLED");
    }
}
