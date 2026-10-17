package com.socialdash.engagement;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface EngagementRepository extends JpaRepository<Engagement, Long>, JpaSpecificationExecutor<Engagement> {

    @Query("select e from Engagement e join fetch e.socialAccount a where e.id = :id and a.user.id = :userId")
    Optional<Engagement> findOwned(Long id, Long userId);

    boolean existsBySocialAccountIdAndExternalId(Long accountId, String externalId);

    @Query("""
            select e.status as status, count(e) as total from Engagement e
            where e.socialAccount.user.id = :userId group by e.status""")
    List<StatusCount> countByStatus(Long userId);

    @Query("""
            select e from Engagement e
            where e.socialAccount.user.id = :userId and e.repliedAt is not null and e.repliedAt >= :since""")
    List<Engagement> findRepliedSince(Long userId, Instant since);

    interface StatusCount {
        Engagement.Status getStatus();
        long getTotal();
    }
}
