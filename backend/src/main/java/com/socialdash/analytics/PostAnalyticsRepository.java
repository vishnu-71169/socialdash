package com.socialdash.analytics;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostAnalyticsRepository extends JpaRepository<PostAnalytics, Long> {

    List<PostAnalytics> findByPostPublicationIdOrderByRecordedAt(Long publicationId);
}
