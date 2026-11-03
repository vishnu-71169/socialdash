package com.socialdash.integration.meta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.socialdash.account.Platform;
import com.socialdash.account.SocialAccount;
import com.socialdash.config.AppProperties;
import com.socialdash.integration.PlatformApiException;
import com.socialdash.integration.SocialPlatformClient.DailyMetrics;
import com.socialdash.integration.SocialPlatformClient.PublishResult;
import com.socialdash.security.TokenCipher;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MetaGraphClientTest {

    private static final String BASE = "https://graph.test/v26.0";
    private static final AppProperties.Meta CONFIG = new AppProperties.Meta(
            "https://graph.test", "v26.0", "", "", 15, 14,
            "views", "page_media_view", "page_actions_post_reactions_total", "post_media_view");

    private MockRestServiceServer server;
    private MetaGraphClient client;
    private TokenCipher cipher;

    @BeforeEach
    void setUp() {
        cipher = new TokenCipher(new AppProperties(null, "c29jaWFsZGFzaC1kZXYtdG9rZW4ta2V5LTMyLWJ5dGU=",
                null, null, null, null, CONFIG));
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new MetaGraphClient(builder.build(), cipher, CONFIG);
    }

    private SocialAccount account(Platform platform, String id, String handle) {
        SocialAccount a = new SocialAccount(null, platform, id, handle, handle, false);
        a.setAccessTokenEncrypted(cipher.encrypt("secret-token"));
        return a;
    }

    @Test
    void instagramDailyMetricsUseTotalValuesAndSendTheTokenAsAHeader() {
        server.expect(requestTo(startsWith(BASE + "/178?")))
                .andExpect(header("Authorization", "Bearer secret-token"))
                .andExpect(queryParam("fields", "followers_count"))
                .andRespond(withSuccess("{\"followers_count\":321,\"id\":\"178\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith(BASE + "/178/insights?")))
                .andExpect(queryParam("metric_type", "total_value"))
                .andRespond(withSuccess("""
                        {"data":[{"name":"views","total_value":{"value":1500}},
                                 {"name":"likes","total_value":{"value":40}},
                                 {"name":"comments","total_value":{"value":7}},
                                 {"name":"shares","total_value":{"value":3}}]}""", MediaType.APPLICATION_JSON));

        DailyMetrics m = client.fetchDailyMetrics(account(Platform.INSTAGRAM, "178", "@shop"),
                LocalDate.now(ZoneOffset.UTC).minusDays(1));

        assertThat(m).isEqualTo(new DailyMetrics(321, 1500, 40, 7, 3));
        server.verify();
    }

    @Test
    void aRejectedMetricIsRetriedAloneSoTheOthersStillCount() {
        String invalidMetric = "{\"error\":{\"message\":\"(#100) The value must be a valid insights metric\",\"code\":100}}";
        server.expect(requestTo(startsWith(BASE + "/42?")))
                .andRespond(withSuccess("{\"followers_count\":10}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith(BASE + "/42/insights?")))
                .andExpect(queryParam("metric", "page_media_view%2Cpage_actions_post_reactions_total")) // comma is URL-encoded
                .andRespond(withBadRequest().body(invalidMetric).contentType(MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith(BASE + "/42/insights?")))
                .andExpect(queryParam("metric", "page_media_view"))
                .andRespond(withBadRequest().body(invalidMetric).contentType(MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith(BASE + "/42/insights?")))
                .andExpect(queryParam("metric", "page_actions_post_reactions_total"))
                .andRespond(withSuccess("""
                        {"data":[{"name":"page_actions_post_reactions_total","period":"day",
                                  "values":[{"value":{"like":5,"love":2}}]}]}""", MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith(BASE + "/42/posts?")))
                .andRespond(withSuccess("""
                        {"data":[{"comments":{"summary":{"total_count":4}},"shares":{"count":2}},
                                 {"comments":{"summary":{"total_count":1}}}]}""", MediaType.APPLICATION_JSON));

        DailyMetrics m = client.fetchDailyMetrics(account(Platform.FACEBOOK, "42", "@page"),
                LocalDate.now(ZoneOffset.UTC).minusDays(1));

        assertThat(m).isEqualTo(new DailyMetrics(10, 0, 7, 5, 2));
        server.verify();
    }

    @Test
    void rateLimitsAreRetryableButRejectedTokensAreNot() {
        server.expect(requestTo(startsWith(BASE + "/1?")))
                .andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"Application request limit reached\",\"code\":4}}"));
        server.expect(requestTo(startsWith(BASE + "/1?")))
                .andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"Session has expired\",\"code\":190}}"));
        SocialAccount ig = account(Platform.INSTAGRAM, "1", "@shop");
        LocalDate day = LocalDate.now(ZoneOffset.UTC);

        assertThatThrownBy(() -> client.fetchDailyMetrics(ig, day))
                .isInstanceOfSatisfying(PlatformApiException.class, e -> assertThat(e.isRetryable()).isTrue());
        assertThatThrownBy(() -> client.fetchDailyMetrics(ig, day))
                .isInstanceOfSatisfying(PlatformApiException.class, e -> {
                    assertThat(e.isRetryable()).isFalse();
                    assertThat(e.getMessage()).contains("invalid or expired");
                });
    }

    @Test
    void publishingReturnsARecentIdenticalPostInsteadOfPostingTwice() {
        String now = OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ"));
        server.expect(requestTo(startsWith(BASE + "/42/posts?")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"data":[{"id":"42_9","message":"Hello","created_time":"%s",
                                  "permalink_url":"https://facebook.com/42_9"}]}""".formatted(now),
                        MediaType.APPLICATION_JSON));

        PublishResult result = client.publish(account(Platform.FACEBOOK, "42", "@page"), "Hello", "publication-1");

        assertThat(result).isEqualTo(new PublishResult("42_9", "https://facebook.com/42_9"));
        server.verify(); // no POST to /feed was made
    }

    @Test
    void textPostsToInstagramAreRejectedWithoutRetrying() {
        assertThatThrownBy(() -> client.publish(account(Platform.INSTAGRAM, "1", "@shop"), "Hi", "publication-2"))
                .isInstanceOfSatisfying(PlatformApiException.class, e -> assertThat(e.isRetryable()).isFalse());
    }
}
