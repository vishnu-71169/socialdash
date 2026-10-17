package com.socialdash.post;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PostPublicationRepository extends JpaRepository<PostPublication, Long> {

    @Query("""
            select p.id from PostPublication p
            where p.status = com.socialdash.post.PublicationStatus.SCHEDULED and p.nextAttemptAt <= :now
            order by p.nextAttemptAt""")
    List<Long> findDueIds(Instant now, Pageable pageable);

    /**
     * Atomically claims a due publication. Only one caller can move a row from SCHEDULED to PUBLISHING, so
     * concurrent scheduler runs (or multiple app instances) never publish the same row twice. Bumping the
     * version makes any concurrent edit through JPA fail with an optimistic-lock error.
     */
    @Modifying
    @Query("""
            update PostPublication p
            set p.status = com.socialdash.post.PublicationStatus.PUBLISHING, p.claimedAt = :now,
                p.attemptCount = p.attemptCount + 1, p.version = p.version + 1
            where p.id = :id and p.status = com.socialdash.post.PublicationStatus.SCHEDULED and p.nextAttemptAt <= :now""")
    int claim(Long id, Instant now);

    /** Returns rows whose worker died mid-publish to the queue. Safe because publishing is idempotent per row. */
    @Modifying
    @Query("""
            update PostPublication p
            set p.status = com.socialdash.post.PublicationStatus.SCHEDULED, p.nextAttemptAt = :now,
                p.claimedAt = null, p.version = p.version + 1
            where p.status = com.socialdash.post.PublicationStatus.PUBLISHING and p.claimedAt < :cutoff""")
    int releaseStuck(Instant cutoff, Instant now);

    @Query("""
            select p from PostPublication p join fetch p.post join fetch p.socialAccount
            where p.id = :id""")
    Optional<PostPublication> findForPublishing(Long id);

    @Query("""
            select p from PostPublication p join fetch p.socialAccount
            where p.status = com.socialdash.post.PublicationStatus.PUBLISHED and p.publishedAt >= :since""")
    List<PostPublication> findPublishedSince(Instant since);

    @Query(value = """
            select p from PostPublication p join fetch p.post join fetch p.socialAccount
            where p.socialAccount.id in :accountIds and p.status = com.socialdash.post.PublicationStatus.PUBLISHED
              and p.publishedAt >= :from and p.publishedAt < :to
            order by (p.likes + p.comments + p.shares) desc, p.publishedAt desc""",
            countQuery = """
            select count(p) from PostPublication p
            where p.socialAccount.id in :accountIds and p.status = com.socialdash.post.PublicationStatus.PUBLISHED
              and p.publishedAt >= :from and p.publishedAt < :to""")
    Page<PostPublication> findTopPublished(Collection<Long> accountIds, Instant from, Instant to, Pageable pageable);

    @Query(value = """
            select p from PostPublication p join fetch p.post join fetch p.socialAccount
            where p.socialAccount.id in :accountIds and p.status = com.socialdash.post.PublicationStatus.PUBLISHED
              and p.publishedAt >= :from and p.publishedAt < :to
            order by p.publishedAt desc""",
            countQuery = """
            select count(p) from PostPublication p
            where p.socialAccount.id in :accountIds and p.status = com.socialdash.post.PublicationStatus.PUBLISHED
              and p.publishedAt >= :from and p.publishedAt < :to""")
    Page<PostPublication> findRecentPublished(Collection<Long> accountIds, Instant from, Instant to, Pageable pageable);

    @Query("""
            select count(p) from PostPublication p
            where p.socialAccount.id in :accountIds and p.status = com.socialdash.post.PublicationStatus.PUBLISHED
              and p.publishedAt >= :from and p.publishedAt < :to""")
    long countPublished(Collection<Long> accountIds, Instant from, Instant to);

    @Query("""
            select p from PostPublication p join fetch p.post join fetch p.socialAccount
            where p.id = :id and p.post.user.id = :userId""")
    Optional<PostPublication> findOwned(Long id, Long userId);
}
