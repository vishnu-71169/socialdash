package com.socialdash.post;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class PostStatusCalculatorTest {

    private static List<PostPublication> pubs(PublicationStatus... statuses) {
        return Arrays.stream(statuses).map(s -> new PostPublication(null, null, s)).toList();
    }

    @Test
    void allPublishedIsPublished() {
        assertThat(PostStatusCalculator.derive(PostStatus.PUBLISHING,
                pubs(PublicationStatus.PUBLISHED, PublicationStatus.PUBLISHED))).isEqualTo(PostStatus.PUBLISHED);
    }

    @Test
    void anyInFlightIsPublishing() {
        assertThat(PostStatusCalculator.derive(PostStatus.SCHEDULED,
                pubs(PublicationStatus.PUBLISHING, PublicationStatus.SCHEDULED))).isEqualTo(PostStatus.PUBLISHING);
    }

    @Test
    void untouchedScheduledStaysScheduled() {
        assertThat(PostStatusCalculator.derive(PostStatus.SCHEDULED,
                pubs(PublicationStatus.SCHEDULED, PublicationStatus.SCHEDULED))).isEqualTo(PostStatus.SCHEDULED);
    }

    @Test
    void retryPendingCountsAsPublishing() {
        List<PostPublication> list = pubs(PublicationStatus.SCHEDULED);
        list.get(0).setAttemptCount(1);
        assertThat(PostStatusCalculator.derive(PostStatus.SCHEDULED, list)).isEqualTo(PostStatus.PUBLISHING);
    }

    @Test
    void partialFailureIsFailedOnceNothingIsPending() {
        assertThat(PostStatusCalculator.derive(PostStatus.PUBLISHING,
                pubs(PublicationStatus.PUBLISHED, PublicationStatus.FAILED))).isEqualTo(PostStatus.FAILED);
    }

    @Test
    void publishedPlusCancelledIsPublished() {
        assertThat(PostStatusCalculator.derive(PostStatus.PUBLISHING,
                pubs(PublicationStatus.PUBLISHED, PublicationStatus.CANCELLED))).isEqualTo(PostStatus.PUBLISHED);
    }

    @Test
    void draftAndCancelled() {
        assertThat(PostStatusCalculator.derive(PostStatus.DRAFT, pubs(PublicationStatus.PENDING)))
                .isEqualTo(PostStatus.DRAFT);
        assertThat(PostStatusCalculator.derive(PostStatus.SCHEDULED,
                pubs(PublicationStatus.CANCELLED, PublicationStatus.CANCELLED))).isEqualTo(PostStatus.CANCELLED);
        assertThat(PostStatusCalculator.derive(PostStatus.DRAFT, List.of())).isEqualTo(PostStatus.DRAFT);
    }
}
