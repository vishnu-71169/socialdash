package com.socialdash.demo;

import com.socialdash.account.AccountDtos.AccountResponse;
import com.socialdash.account.AccountDtos.ConnectAccountRequest;
import com.socialdash.account.Platform;
import com.socialdash.account.SocialAccount;
import com.socialdash.account.SocialAccountRepository;
import com.socialdash.account.SocialAccountService;
import com.socialdash.analytics.AnalyticsCollector;
import com.socialdash.engagement.Engagement;
import com.socialdash.engagement.EngagementRepository;
import com.socialdash.integration.SimulatedPlatformClient;
import com.socialdash.integration.SocialPlatformClient.PostMetrics;
import com.socialdash.listening.ListeningDtos.AddKeywordRequest;
import com.socialdash.listening.ListeningService;
import com.socialdash.post.Post;
import com.socialdash.post.PostDtos.PostAction;
import com.socialdash.post.PostDtos.PostRequest;
import com.socialdash.post.PostPublication;
import com.socialdash.post.PostRepository;
import com.socialdash.post.PostService;
import com.socialdash.post.PublicationStatus;
import com.socialdash.user.User;
import com.socialdash.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Creates a demo user with simulated accounts, post history, keywords and interactions so the dashboard has
 * something to show on first run. Disable with DEMO_SEED=false.
 */
@Component
@ConditionalOnProperty(name = "app.demo.seed", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    public static final String DEMO_EMAIL = "demo@socialdash.dev";
    public static final String DEMO_PASSWORD = "demo1234";

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private static final String[] PUBLISHED_CONTENT = {
            "5 lessons we learned launching our new product line this quarter. Thread below!",
            "Behind the scenes: how our team plans a month of content in one afternoon.",
            "We just crossed a big milestone - thank you to everyone who has supported us!",
            "Quick tip: post when your audience is online. Check your analytics, not a generic chart.",
            "New blog post: measuring engagement rate the right way (and why impressions matter).",
            "Meet the team! This week we're featuring our amazing customer support crew.",
            "Poll time: what kind of content do you want to see more of from us?",
            "Our spring collection is live. Which piece is your favourite?",
            "Case study: how a small bakery doubled its reach with consistent scheduling.",
            "Friday reminder: done is better than perfect. Ship it!",
            "Live Q&A tomorrow at 5pm - drop your questions in the comments.",
            "Three tools we use every day to keep our social calendar on track.",
    };

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final SocialAccountService accountService;
    private final SocialAccountRepository accountRepository;
    private final ListeningService listeningService;
    private final PostService postService;
    private final PostRepository postRepository;
    private final EngagementRepository engagementRepository;
    private final AnalyticsCollector analyticsCollector;
    private final SimulatedPlatformClient simulated;
    private final TransactionTemplate tx;

    public DemoDataSeeder(UserRepository users, PasswordEncoder passwordEncoder, SocialAccountService accountService,
                          SocialAccountRepository accountRepository, ListeningService listeningService,
                          PostService postService, PostRepository postRepository,
                          EngagementRepository engagementRepository, AnalyticsCollector analyticsCollector,
                          SimulatedPlatformClient simulated, TransactionTemplate tx) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.accountService = accountService;
        this.accountRepository = accountRepository;
        this.listeningService = listeningService;
        this.postService = postService;
        this.postRepository = postRepository;
        this.engagementRepository = engagementRepository;
        this.analyticsCollector = analyticsCollector;
        this.simulated = simulated;
        this.tx = tx;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.existsByEmailIgnoreCase(DEMO_EMAIL)) {
            return;
        }
        log.info("Seeding demo data for {}", DEMO_EMAIL);
        tx.executeWithoutResult(s -> seed());
        log.info("Demo data ready. Sign in with {} / {}", DEMO_EMAIL, DEMO_PASSWORD);
    }

    private void seed() {
        User user = users.save(new User("Demo User", DEMO_EMAIL, passwordEncoder.encode(DEMO_PASSWORD)));
        Long userId = user.getId();

        List<AccountResponse> connected = List.of(
                accountService.connectSimulated(userId, new ConnectAccountRequest(Platform.INSTAGRAM, "brewbean.co", "Brew & Bean")),
                accountService.connectSimulated(userId, new ConnectAccountRequest(Platform.TWITTER, "brewbean", "Brew & Bean")),
                accountService.connectSimulated(userId, new ConnectAccountRequest(Platform.LINKEDIN, "brew_and_bean", "Brew & Bean Ltd")),
                accountService.connectSimulated(userId, new ConnectAccountRequest(Platform.FACEBOOK, "brewandbean", "Brew & Bean Coffee")));
        List<SocialAccount> accounts = connected.stream()
                .map(a -> accountRepository.findById(a.id()).orElseThrow()).toList();

        listeningService.addKeyword(userId, new AddKeywordRequest("Brew and Bean", null));
        listeningService.addKeyword(userId, new AddKeywordRequest("#coffeelovers", Platform.INSTAGRAM));
        listeningService.addKeyword(userId, new AddKeywordRequest("cold brew", null));

        seedPublishedPosts(user, accounts);
        seedFailedPost(user, accounts);

        Instant now = Instant.now();
        List<Long> twoAccounts = accounts.subList(0, 2).stream().map(SocialAccount::getId).toList();
        List<Long> allAccounts = accounts.stream().map(SocialAccount::getId).toList();
        postService.create(userId, new PostRequest("Weekend special: 2-for-1 cold brew all Saturday!", null,
                twoAccounts, now.plus(Duration.ofHours(20)).truncatedTo(ChronoUnit.HOURS), PostAction.SCHEDULE));
        postService.create(userId, new PostRequest("We're hiring baristas in three new locations. Apply via the link in bio.",
                null, allAccounts, now.plus(Duration.ofDays(2)).truncatedTo(ChronoUnit.HOURS), PostAction.SCHEDULE));
        postService.create(userId, new PostRequest("Sneak peek of our autumn menu... pumpkin spice is back?", null,
                List.of(accounts.get(0).getId()), now.plus(Duration.ofDays(4)).truncatedTo(ChronoUnit.HOURS),
                PostAction.SCHEDULE));
        postService.create(userId, new PostRequest("Draft: customer spotlight series - who should we feature first?",
                null, twoAccounts, null, PostAction.DRAFT));

        seedEngagementStates(accounts);
    }

    private void seedPublishedPosts(User user, List<SocialAccount> accounts) {
        Random r = new Random(42);
        Instant now = Instant.now();
        for (int i = 0; i < PUBLISHED_CONTENT.length; i++) {
            Instant publishedAt = now.minus(Duration.ofHours(10 + (long) i * 52 + r.nextInt(12)));
            Post post = new Post(user, PUBLISHED_CONTENT[i], null);
            post.setCreatedAt(publishedAt.minus(Duration.ofDays(1)));
            post.setScheduledAt(publishedAt);

            List<SocialAccount> targets = new ArrayList<>();
            for (SocialAccount a : accounts) {
                if (r.nextDouble() < 0.6 && PUBLISHED_CONTENT[i].length() <= a.getPlatform().getMaxPostLength()) {
                    targets.add(a);
                }
            }
            if (targets.isEmpty()) targets.add(accounts.get(i % accounts.size()));

            for (SocialAccount a : targets) {
                PostPublication pub = new PostPublication(post, a, PublicationStatus.SCHEDULED);
                pub.setAttemptCount(1);
                String externalId = a.getPlatform().name().toLowerCase(Locale.ROOT) + "_" + UUID.randomUUID();
                pub.markPublished(externalId, "https://sim." + a.getPlatform().name().toLowerCase(Locale.ROOT)
                        + ".example/" + a.getHandle().substring(1) + "/posts/" + externalId, publishedAt);
                post.getPublications().add(pub);
            }
            post.refreshStatus();
            postRepository.save(post);

            for (PostPublication pub : post.getPublications()) {
                List<Instant> points = new ArrayList<>();
                List<PostMetrics> metrics = new ArrayList<>();
                for (Instant t = publishedAt.plus(Duration.ofHours(2)); t.isBefore(now); t = t.plus(Duration.ofHours(6))) {
                    points.add(t);
                    metrics.add(simulated.postMetricsAt(pub.getSocialAccount(), pub.getExternalPostId(), publishedAt, t));
                }
                analyticsCollector.recordPostHistory(pub, points, metrics);
                analyticsCollector.recordPostMetrics(pub, now, false);
            }
        }
    }

    private void seedFailedPost(User user, List<SocialAccount> accounts) {
        Post post = new Post(user, "Flash sale starts NOW - 30% off all beans for the next 3 hours!", null);
        Instant at = Instant.now().minus(Duration.ofHours(30));
        post.setCreatedAt(at.minus(Duration.ofHours(2)));
        post.setScheduledAt(at);
        PostPublication ok = new PostPublication(post, accounts.get(0), PublicationStatus.SCHEDULED);
        ok.setAttemptCount(1);
        String externalId = "instagram_" + UUID.randomUUID();
        ok.markPublished(externalId, null, at);
        PostPublication failed = new PostPublication(post, accounts.get(1), PublicationStatus.SCHEDULED);
        failed.setAttemptCount(3);
        failed.markFailed("Simulated TWITTER API rate limit (HTTP 429)");
        post.getPublications().add(ok);
        post.getPublications().add(failed);
        post.refreshStatus();
        postRepository.save(post);
    }

    private void seedEngagementStates(List<SocialAccount> accounts) {
        Random r = new Random(7);
        Instant cutoff = Instant.now().minus(Duration.ofHours(12));
        List<Long> accountIds = accounts.stream().map(SocialAccount::getId).toList();
        for (Engagement e : engagementRepository.findAll()) {
            if (!accountIds.contains(e.getSocialAccount().getId()) || e.getReceivedAt().isAfter(cutoff)) continue;
            double roll = r.nextDouble();
            if (roll < 0.35) {
                e.markReplied("Thanks so much for reaching out! We'll follow up shortly.",
                        e.getReceivedAt().plus(Duration.ofMinutes(15 + r.nextInt(300))));
            } else if (roll < 0.6) {
                e.resolve(e.getReceivedAt().plus(Duration.ofMinutes(30 + r.nextInt(600))));
            }
        }
    }
}
