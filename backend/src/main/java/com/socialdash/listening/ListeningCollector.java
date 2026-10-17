package com.socialdash.listening;

import com.socialdash.integration.PlatformApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class ListeningCollector {

    private static final Logger log = LoggerFactory.getLogger(ListeningCollector.class);

    private final TrackedKeywordRepository keywords;
    private final ListeningService listeningService;
    private final TransactionTemplate tx;

    public ListeningCollector(TrackedKeywordRepository keywords, ListeningService listeningService,
                              TransactionTemplate tx) {
        this.keywords = keywords;
        this.listeningService = listeningService;
        this.tx = tx;
    }

    @Scheduled(fixedDelayString = "${app.scheduler.listening-interval-ms:120000}", initialDelay = 15000)
    public void collectAll() {
        for (TrackedKeyword k : keywords.findAll()) {
            try {
                tx.executeWithoutResult(s -> keywords.findById(k.getId()).ifPresent(listeningService::pollSinceLastCheck));
            } catch (PlatformApiException e) {
                log.warn("Skipping keyword {} this round: {}", k.getId(), e.getMessage());
            }
        }
    }
}
