package com.socialdash.analytics;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AnalyticsSnapshotRepository extends JpaRepository<AnalyticsSnapshot, Long> {

    Optional<AnalyticsSnapshot> findBySocialAccountIdAndSnapshotDate(Long accountId, LocalDate date);

    Optional<AnalyticsSnapshot> findTopBySocialAccountIdOrderBySnapshotDateDesc(Long accountId);

    @Query("""
            select new com.socialdash.analytics.DailyTotals(
                s.snapshotDate, sum(s.followers), sum(s.impressions), sum(s.likes), sum(s.comments), sum(s.shares))
            from AnalyticsSnapshot s
            where s.socialAccount.id in :accountIds and s.snapshotDate between :start and :end
            group by s.snapshotDate
            order by s.snapshotDate""")
    List<DailyTotals> dailyTotals(Collection<Long> accountIds, LocalDate start, LocalDate end);

    @Query("""
            select new com.socialdash.analytics.AccountTotals(
                s.socialAccount.id, sum(s.impressions), sum(s.likes), sum(s.comments), sum(s.shares))
            from AnalyticsSnapshot s
            where s.socialAccount.id in :accountIds and s.snapshotDate between :start and :end
            group by s.socialAccount.id""")
    List<AccountTotals> accountTotals(Collection<Long> accountIds, LocalDate start, LocalDate end);

    /** Earliest snapshot per account within the range: the follower baseline for growth. */
    @Query("""
            select s from AnalyticsSnapshot s
            where s.socialAccount.id in :accountIds and s.snapshotDate = (
                select min(s2.snapshotDate) from AnalyticsSnapshot s2
                where s2.socialAccount = s.socialAccount and s2.snapshotDate between :start and :end)""")
    List<AnalyticsSnapshot> firstInRange(Collection<Long> accountIds, LocalDate start, LocalDate end);

    /** Latest snapshot per account within the range: the follower count as of the range end. */
    @Query("""
            select s from AnalyticsSnapshot s
            where s.socialAccount.id in :accountIds and s.snapshotDate = (
                select max(s2.snapshotDate) from AnalyticsSnapshot s2
                where s2.socialAccount = s.socialAccount and s2.snapshotDate between :start and :end)""")
    List<AnalyticsSnapshot> lastInRange(Collection<Long> accountIds, LocalDate start, LocalDate end);
}
