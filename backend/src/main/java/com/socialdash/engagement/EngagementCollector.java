package com.socialdash.engagement;

import com.socialdash.account.SocialAccount;
import com.socialdash.account.SocialAccountRepository;
import com.socialdash.integration.PlatformApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class EngagementCollector {

    private static final Logger log = LoggerFactory.getLogger(EngagementCollector.class);

    private final SocialAccountRepository accounts;
    private final EngagementService engagementService;
    private final TransactionTemplate tx;

    public EngagementCollector(SocialAccountRepository accounts, EngagementService engagementService,
                               TransactionTemplate tx) {
        this.accounts = accounts;
        this.engagementService = engagementService;
        this.tx = tx;
    }

    @Scheduled(fixedDelayString = "${app.scheduler.engagement-interval-ms:180000}", initialDelay = 20000)
    public void collectAll() {
        for (SocialAccount account : accounts.findAll()) {
            try {
                tx.executeWithoutResult(s ->
                        engagementService.syncAccount(accounts.findById(account.getId()).orElseThrow()));
            } catch (PlatformApiException e) {
                log.warn("Skipping interactions for account {}: {}", account.getId(), e.getMessage());
            }
        }
    }
}
