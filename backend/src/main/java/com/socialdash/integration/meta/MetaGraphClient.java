package com.socialdash.integration.meta;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialdash.account.Platform;
import com.socialdash.account.SocialAccount;
import com.socialdash.config.AppProperties;
import com.socialdash.integration.PlatformApiException;
import com.socialdash.integration.SocialPlatformClient;
import com.socialdash.security.TokenCipher;
import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Live client for Instagram professional accounts and Facebook Pages, both served by the Meta Graph API.
 * The access token is sent in the Authorization header so it never appears in URLs or logs.
 *
 * <p>Known limits: Instagram only accepts media posts, so text publishing is rejected; keyword search is not part of
 * these APIs, so social listening stays simulated; Facebook daily comments/shares are counted on posts created that day.
 */
@Component
public class MetaGraphClient implements SocialPlatformClient {

    private static final Logger log = LoggerFactory.getLogger(MetaGraphClient.class);
    /** Graph error codes for throttling and temporary outages. */
    private static final Set<Integer> TRANSIENT_CODES = Set.of(1, 2, 4, 17, 32, 341, 368, 613, 80001, 80002, 80004);
    private static final DateTimeFormatter GRAPH_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");
    private static final Duration FOLLOWERS_CACHE = Duration.ofMinutes(5);
    /** A post with identical text this recent is treated as an earlier attempt that succeeded. */
    private static final Duration DUPLICATE_WINDOW = Duration.ofHours(1);
    private static final int RECENT_MEDIA_LIMIT = 10;

    public record Profile(String id, String handle, String displayName, String accessToken, long followers) {}

    private record CachedCount(long value, Instant at) {}

    private final RestClient http;
    private final TokenCipher tokenCipher;
    private final AppProperties.Meta config;
    private final ObjectMapper json = new ObjectMapper();
    private final Map<Long, CachedCount> followersCache = new ConcurrentHashMap<>();

    @Autowired
    public MetaGraphClient(RestClient.Builder builder, TokenCipher tokenCipher, AppProperties props) {
        this(builder.baseUrl(props.meta().baseUrl() + "/" + props.meta().apiVersion())
                        .requestFactory(timeouts())
                        .build(),
                tokenCipher, props.meta());
    }

    MetaGraphClient(RestClient http, TokenCipher tokenCipher, AppProperties.Meta config) {
        this.http = http;
        this.tokenCipher = tokenCipher;
        this.config = config;
    }

    private static JdkClientHttpRequestFactory timeouts() {
        var factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        factory.setReadTimeout(Duration.ofSeconds(20));
        return factory;
    }

    // ---------------------------------------------------------------- connecting

    /**
     * Checks a token against the account it should belong to and returns the profile plus the token to store. For
     * Facebook, a user or Page token is accepted; with app credentials configured it is first exchanged for a
     * long-lived token, and the Page token derived from that does not expire.
     */
    public Profile verify(Platform platform, String accountId, String rawToken) {
        if (platform == Platform.INSTAGRAM) {
            JsonNode me = get(rawToken, "/" + accountId, Map.of("fields", "id,username,name,followers_count"));
            String username = me.path("username").asText(accountId);
            return new Profile(me.path("id").asText(accountId), "@" + username,
                    me.path("name").asText(username), rawToken, me.path("followers_count").asLong());
        }
        if (platform != Platform.FACEBOOK) {
            throw new PlatformApiException(platform + " is not a Meta platform", false);
        }
        String token = rawToken;
        if (config.canExchangeTokens()) {
            try {
                token = get(null, "/oauth/access_token", Map.of(
                        "grant_type", "fb_exchange_token",
                        "client_id", config.appId(),
                        "client_secret", config.appSecret(),
                        "fb_exchange_token", rawToken)).path("access_token").asText(rawToken);
            } catch (PlatformApiException e) {
                if (e.isRetryable()) throw e;
                log.info("Token exchange skipped: {}", e.getMessage());
            }
        }
        // With a user token this returns the Page's own token; with a Page token it returns the same token or nothing.
        try {
            String pageToken = get(token, "/" + accountId, Map.of("fields", "access_token")).path("access_token").asText("");
            if (!pageToken.isBlank()) token = pageToken;
        } catch (PlatformApiException e) {
            if (e.isRetryable()) throw e;
        }
        JsonNode page = get(token, "/" + accountId, Map.of("fields", "id,name,username,followers_count,fan_count"));
        String name = page.path("name").asText(accountId);
        String handle = "@" + page.path("username").asText(name.replaceAll("[^A-Za-z0-9_.]", ""));
        long followers = page.hasNonNull("followers_count") ? page.path("followers_count").asLong()
                : page.path("fan_count").asLong();
        return new Profile(page.path("id").asText(accountId), handle, name, token, followers);
    }

    // ---------------------------------------------------------------- analytics

    @Override
    public DailyMetrics fetchDailyMetrics(SocialAccount account, LocalDate date) {
        String token = token(account);
        String id = account.getPlatformAccountId();
        long since = date.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        long until = Math.min(since + 86_400, Instant.now().getEpochSecond());
        Map<String, Object> range = Map.of("period", "day", "since", since, "until", until);
        long followers = followers(account, token);

        if (account.getPlatform() == Platform.INSTAGRAM) {
            String views = config.instagramViewsMetric();
            Map<String, Long> m = insights(token, "/" + id + "/insights",
                    List.of(views, "likes", "comments", "shares"), withExtra(range, "metric_type", "total_value"));
            return new DailyMetrics(followers, m.get(views), m.get("likes"), m.get("comments"), m.get("shares"));
        }

        String views = config.facebookViewsMetric();
        String reactions = config.facebookReactionsMetric();
        Map<String, Long> m = insights(token, "/" + id + "/insights", List.of(views, reactions), range);
        long comments = 0;
        long shares = 0;
        JsonNode posts = get(token, "/" + id + "/posts", Map.of("since", since, "until", until, "limit", 100,
                "fields", "comments.summary(true).limit(0),shares"));
        for (JsonNode post : posts.path("data")) {
            comments += post.path("comments").path("summary").path("total_count").asLong();
            shares += post.path("shares").path("count").asLong();
        }
        return new DailyMetrics(followers, m.get(views), m.get(reactions), comments, shares);
    }

    @Override
    public PostMetrics fetchPostMetrics(SocialAccount account, String externalPostId, Instant publishedAt) {
        String token = token(account);
        if (account.getPlatform() == Platform.INSTAGRAM) {
            String views = config.instagramViewsMetric();
            Map<String, Long> m = insights(token, "/" + externalPostId + "/insights",
                    List.of(views, "likes", "comments", "shares"), Map.of());
            return new PostMetrics(m.get(views), m.get("likes"), m.get("comments"), m.get("shares"));
        }
        JsonNode post = get(token, "/" + externalPostId, Map.of("fields",
                "reactions.summary(true).limit(0),comments.summary(true).limit(0),shares"));
        String views = config.facebookPostViewsMetric();
        long impressions = insights(token, "/" + externalPostId + "/insights", List.of(views), Map.of()).get(views);
        return new PostMetrics(impressions,
                post.path("reactions").path("summary").path("total_count").asLong(),
                post.path("comments").path("summary").path("total_count").asLong(),
                post.path("shares").path("count").asLong());
    }

    // ---------------------------------------------------------------- publishing

    /**
     * Meta has no idempotency keys, so before posting this looks for a very recent post with the same text: if a
     * previous attempt timed out after Facebook accepted it, that post is returned instead of posting twice.
     */
    @Override
    public PublishResult publish(SocialAccount account, String content, String idempotencyKey) {
        if (account.getPlatform() == Platform.INSTAGRAM) {
            throw new PlatformApiException("Instagram only accepts photo or video posts; text-only publishing "
                    + "isn't supported by its API", false);
        }
        String token = token(account);
        String pageId = account.getPlatformAccountId();
        JsonNode recent = get(token, "/" + pageId + "/posts",
                Map.of("fields", "id,message,created_time,permalink_url", "limit", 10));
        Instant cutoff = Instant.now().minus(DUPLICATE_WINDOW);
        for (JsonNode post : recent.path("data")) {
            if (content.equals(post.path("message").asText(null)) && parseTime(post.path("created_time")).isAfter(cutoff)) {
                log.info("Publication {} already on Facebook as {}; not posting again", idempotencyKey,
                        post.path("id").asText());
                return new PublishResult(post.path("id").asText(), permalink(post));
            }
        }
        String postId = post(token, "/" + pageId + "/feed", Map.of("message", content)).path("id").asText();
        String url = "https://www.facebook.com/" + postId;
        try {
            url = permalink(get(token, "/" + postId, Map.of("fields", "permalink_url")));
        } catch (PlatformApiException e) {
            log.debug("Permalink lookup failed for {}: {}", postId, e.getMessage());
        }
        return new PublishResult(postId, url);
    }

    // ---------------------------------------------------------------- listening / engagement

    /** Keyword search isn't offered by these APIs; {@code PlatformClientRegistry} keeps listening simulated. */
    @Override
    public List<RawMention> searchMentions(String keyword, Platform platform, Instant since, Instant until) {
        return List.of();
    }

    /** Comments on the account's most recent posts, newest first, within the requested window. */
    @Override
    public List<RawEngagement> fetchEngagements(SocialAccount account, Instant since, Instant until) {
        String token = token(account);
        String id = account.getPlatformAccountId();
        boolean instagram = account.getPlatform() == Platform.INSTAGRAM;
        JsonNode media = get(token, "/" + id + (instagram ? "/media" : "/posts"),
                Map.of("fields", "id", "limit", RECENT_MEDIA_LIMIT));
        List<RawEngagement> result = new ArrayList<>();
        for (JsonNode item : media.path("data")) {
            JsonNode comments = instagram
                    ? get(token, "/" + item.path("id").asText() + "/comments",
                            Map.of("fields", "id,text,username,timestamp", "limit", 50))
                    : get(token, "/" + item.path("id").asText() + "/comments",
                            Map.of("fields", "id,message,from{id,name},created_time", "filter", "stream",
                                    "since", since.getEpochSecond(), "limit", 50));
            for (JsonNode c : comments.path("data")) {
                Instant at = parseTime(c.path(instagram ? "timestamp" : "created_time"));
                if (at.isBefore(since) || !at.isBefore(until)) continue;
                String author = instagram ? c.path("username").asText("instagram user")
                        : c.path("from").path("name").asText("Facebook user");
                boolean ownComment = instagram ? ("@" + author).equalsIgnoreCase(account.getHandle())
                        : id.equals(c.path("from").path("id").asText());
                if (ownComment) continue;
                result.add(new RawEngagement(c.path("id").asText(), RawEngagementType.COMMENT, author,
                        "@" + author.replaceAll("\\s+", ""), c.path(instagram ? "text" : "message").asText(""), at));
            }
        }
        return result;
    }

    @Override
    public void reply(SocialAccount account, String engagementExternalId, String message) {
        String edge = account.getPlatform() == Platform.INSTAGRAM ? "/replies" : "/comments";
        post(token(account), "/" + engagementExternalId + edge, Map.of("message", message));
    }

    // ---------------------------------------------------------------- helpers

    private String token(SocialAccount account) {
        if (account.getAccessTokenEncrypted() == null) {
            throw new PlatformApiException("No access token stored for " + account.getHandle(), false);
        }
        return tokenCipher.decrypt(account.getAccessTokenEncrypted());
    }

    private long followers(SocialAccount account, String token) {
        CachedCount cached = account.getId() == null ? null : followersCache.get(account.getId());
        if (cached != null && cached.at().isAfter(Instant.now().minus(FOLLOWERS_CACHE))) {
            return cached.value();
        }
        String fields = account.getPlatform() == Platform.INSTAGRAM ? "followers_count" : "followers_count,fan_count";
        JsonNode node = get(token, "/" + account.getPlatformAccountId(), Map.of("fields", fields));
        long value = node.hasNonNull("followers_count") ? node.path("followers_count").asLong()
                : node.path("fan_count").asLong();
        if (account.getId() != null) {
            followersCache.put(account.getId(), new CachedCount(value, Instant.now()));
        }
        return value;
    }

    /**
     * Fetches several insights metrics in one call. If Meta rejects the combination (usually one renamed or retired
     * metric), each is retried on its own and any that still fail count as 0, so one bad metric can't blank a chart.
     */
    Map<String, Long> insights(String token, String path, List<String> metrics, Map<String, Object> params) {
        Map<String, Long> result = new LinkedHashMap<>();
        metrics.forEach(m -> result.put(m, 0L));
        try {
            readInsights(get(token, path, withExtra(params, "metric", String.join(",", metrics))), result);
        } catch (PlatformApiException e) {
            if (e.isRetryable()) throw e;
            if (metrics.size() == 1) {
                log.warn("Insights metric {} unavailable at {}: {}", metrics.get(0), path, e.getMessage());
                return result;
            }
            for (String metric : metrics) {
                try {
                    readInsights(get(token, path, withExtra(params, "metric", metric)), result);
                } catch (PlatformApiException single) {
                    if (single.isRetryable()) throw single;
                    log.warn("Insights metric {} unavailable at {}: {}", metric, path, single.getMessage());
                }
            }
        }
        return result;
    }

    /** Handles both response shapes: {@code total_value.value} and a {@code values[]} series (numbers or maps). */
    private static void readInsights(JsonNode response, Map<String, Long> into) {
        for (JsonNode metric : response.path("data")) {
            String name = metric.path("name").asText();
            if (!into.containsKey(name)) continue;
            long total;
            if (metric.has("total_value")) {
                total = metric.path("total_value").path("value").asLong();
            } else {
                total = 0;
                for (JsonNode point : metric.path("values")) {
                    total += sum(point.path("value"));
                }
            }
            into.put(name, total);
        }
    }

    private static long sum(JsonNode value) {
        if (value.isNumber()) return value.asLong();
        long total = 0;
        if (value.isObject()) {
            for (JsonNode v : value) total += v.asLong();
        }
        return total;
    }

    private static Map<String, Object> withExtra(Map<String, Object> params, String key, Object value) {
        Map<String, Object> copy = new LinkedHashMap<>(params);
        copy.put(key, value);
        return copy;
    }

    private static Instant parseTime(JsonNode node) {
        try {
            return OffsetDateTime.parse(node.asText(), GRAPH_TIME).toInstant();
        } catch (RuntimeException e) {
            return Instant.EPOCH;
        }
    }

    private static String permalink(JsonNode post) {
        return post.path("permalink_url").asText("https://www.facebook.com/" + post.path("id").asText());
    }

    JsonNode get(String token, String path, Map<String, ?> params) {
        return call(() -> http.get()
                .uri(b -> {
                    b.path(path);
                    params.forEach((k, v) -> b.queryParam(k, "{" + k + "}"));
                    return b.build(params);
                })
                .headers(h -> auth(h, token))
                .retrieve()
                .body(String.class));
    }

    JsonNode post(String token, String path, Map<String, String> form) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        form.forEach(body::add);
        return call(() -> http.post()
                .uri(b -> b.path(path).build())
                .headers(h -> auth(h, token))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .body(String.class));
    }

    private static void auth(HttpHeaders headers, String token) {
        if (token != null) headers.setBearerAuth(token);
    }

    private JsonNode call(Supplier<String> request) {
        try {
            String body = request.get();
            return body == null ? json.createObjectNode() : json.readTree(body);
        } catch (RestClientResponseException e) {
            throw toPlatformError(e);
        } catch (ResourceAccessException e) {
            throw new PlatformApiException("Meta API unreachable: " + e.getMessage(), true);
        } catch (IOException e) {
            throw new PlatformApiException("Unreadable Meta API response", true);
        }
    }

    private PlatformApiException toPlatformError(RestClientResponseException e) {
        int code = 0;
        String message = e.getStatusText();
        boolean transientFlag = false;
        try {
            JsonNode error = json.readTree(e.getResponseBodyAsString()).path("error");
            code = error.path("code").asInt();
            message = error.path("message").asText(message);
            transientFlag = error.path("is_transient").asBoolean(false);
        } catch (IOException | RuntimeException ignored) {
            // not JSON; fall back to the HTTP status
        }
        boolean retryable = transientFlag || TRANSIENT_CODES.contains(code) || e.getStatusCode().is5xxServerError();
        String prefix = code == 190 ? "Access token invalid or expired - generate a new one and connect again. " : "";
        return new PlatformApiException(prefix + "Meta API" + (code > 0 ? " #" + code : "") + ": " + message,
                retryable);
    }
}
