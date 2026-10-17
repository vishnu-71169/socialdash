package com.socialdash.post;

import com.socialdash.account.SocialAccount;
import com.socialdash.config.AppProperties;
import com.socialdash.integration.PlatformApiException;
import com.socialdash.integration.PlatformClientRegistry;
import com.socialdash.integration.SocialPlatformClient.PublishResult;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Publishes due posts. Each publication goes through three short transactions:
 * <ol>
 *   <li>claim: conditional UPDATE SCHEDULED -> PUBLISHING (only one worker can win)</li>
 *   <li>the platform API call, outside any transaction so no DB connection/lock is held during network I/O</li>
 *   <li>record the outcome: PUBLISHED, back to SCHEDULED with exponential backoff, or FAILED</li>
 * </ol>
 * The publication id is sent as the platform idempotency key, so a retry after an ambiguous failure
 * (or after a crash between steps 2 and 3) cannot create a duplicate post.
 */
@Component
public class PublishingScheduler {

    private static final Logger log = LoggerFactory.getLogger(PublishingScheduler.class);

    private final PostPublicationRepository publications;
    private final PlatformClientRegistry clients;
    private final TransactionTemplate tx;
    private final AppProperties.Publishing config;

    public PublishingScheduler(PostPublicationRepository publications, PlatformClientRegistry clients,
                               TransactionTemplate tx, AppProperties props) {
        this.publications = publications;
        this.clients = clients;
        this.tx = tx;
        this.config = props.publishing();
    }

    @Scheduled(fixedDelayString = "${app.scheduler.publish-interval-ms:15000}", initialDelay = 5000)
    public void run() {
        Instant now = Instant.now();
        Integer released = tx.execute(s ->
                publications.releaseStuck(now.minusSeconds(config.stuckTimeoutSeconds()), now));
        if (released != null && released > 0) {
            log.warn("Re-queued {} publication(s) stuck in PUBLISHING", released);
        }
        List<Long> due = publications.findDueIds(now, PageRequest.of(0, config.batchSize()));
        for (Long id : due) {
            try {
                process(id);
            } catch (RuntimeException e) {
                log.error("Unexpected error while publishing publication {}", id, e);
            }
        }
    }

    record Job(SocialAccount account, String content, String idempotencyKey, int attempt) {}

    void process(Long publicationId) {
        Instant now = Instant.now();
        Integer claimed = tx.execute(s -> publications.claim(publicationId, now));
        if (claimed == null || claimed == 0) {
            return; // cancelled, edited or claimed by another worker in the meantime
        }

        Job job = tx.execute(s -> {
            PostPublication pub = publications.findForPublishing(publicationId).orElseThrow();
            pub.getPost().refreshStatus();
            return new Job(pub.getSocialAccount(), pub.getPost().getContent(), "publication-" + pub.getId(),
                    pub.getAttemptCount());
        });

        PublishResult result = null;
        PlatformApiException error = null;
        try {
            result = clients.forAccount(job.account()).publish(job.account(), job.content(), job.idempotencyKey());
        } catch (PlatformApiException e) {
            error = e;
        } catch (RuntimeException e) {
            error = new PlatformApiException("Unexpected error: " + e.getMessage(), true);
        }

        final PublishResult ok = result;
        final PlatformApiException failure = error;
        tx.executeWithoutResult(s -> {
            PostPublication pub = publications.findForPublishing(publicationId).orElseThrow();
            Instant done = Instant.now();
            if (ok != null) {
                pub.markPublished(ok.externalPostId(), ok.url(), done);
                log.info("Published publication {} to {} (attempt {})", publicationId,
                        job.account().getPlatform(), job.attempt());
            } else if (failure.isRetryable() && pub.getAttemptCount() < config.maxAttempts()) {
                Instant next = done.plus(backoff(pub.getAttemptCount()));
                pub.markRetry(failure.getMessage(), next);
                log.info("Publication {} failed (attempt {}), retrying at {}: {}", publicationId,
                        pub.getAttemptCount(), next, failure.getMessage());
            } else {
                pub.markFailed(failure.getMessage());
                log.warn("Publication {} failed permanently: {}", publicationId, failure.getMessage());
            }
            pub.getPost().refreshStatus();
        });
    }

    Duration backoff(int attempt) {
        long factor = 1L << Math.max(0, Math.min(attempt - 1, 10));
        return Duration.ofSeconds(config.retryBaseDelaySeconds() * factor);
    }
}
