package com.socialdash.listening;

import com.socialdash.account.Platform;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface SocialMentionRepository extends JpaRepository<SocialMention, Long>,
        JpaSpecificationExecutor<SocialMention> {

    boolean existsByTrackedKeywordIdAndPlatformAndExternalId(Long keywordId,
                                                             Platform platform,
                                                             String externalId);

    @Query("""
            select m.trackedKeyword.id as keywordId, count(m) as total from SocialMention m
            where m.trackedKeyword.id in :keywordIds and m.detectedAt >= :since
            group by m.trackedKeyword.id""")
    List<KeywordCount> countSince(Collection<Long> keywordIds, Instant since);

    /** Only the columns needed to bucket mentions by day; avoids loading content for volume charts. */
    @Query("""
            select m.trackedKeyword.id as keywordId, m.detectedAt as detectedAt from SocialMention m
            where m.trackedKeyword.id in :keywordIds and m.detectedAt >= :from and m.detectedAt < :to""")
    List<MentionTime> findTimes(Collection<Long> keywordIds, Instant from, Instant to);

    @Query("""
            select m.content from SocialMention m
            where m.trackedKeyword.id in :keywordIds and m.detectedAt >= :from and m.detectedAt < :to""")
    List<String> findContents(Collection<Long> keywordIds, Instant from, Instant to);

    interface KeywordCount {
        Long getKeywordId();
        long getTotal();
    }

    interface MentionTime {
        Long getKeywordId();
        Instant getDetectedAt();
    }
}
