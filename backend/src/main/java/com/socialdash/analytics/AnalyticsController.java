package com.socialdash.analytics;

import com.socialdash.account.Platform;
import com.socialdash.analytics.AnalyticsDtos.Breakdown;
import com.socialdash.analytics.AnalyticsDtos.OverviewResponse;
import com.socialdash.analytics.AnalyticsDtos.PostHistoryPoint;
import com.socialdash.analytics.AnalyticsDtos.PostPerformance;
import com.socialdash.analytics.AnalyticsDtos.TrendPoint;
import com.socialdash.analytics.AnalyticsService.Filter;
import com.socialdash.common.PageResponse;
import com.socialdash.security.AuthUser;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** All endpoints accept optional platform, accountId, startDate and endDate (ISO dates, UTC days). */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analytics;

    public AnalyticsController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/overview")
    public OverviewResponse overview(@AuthenticationPrincipal AuthUser user,
                                     @RequestParam(required = false) Platform platform,
                                     @RequestParam(required = false) Long accountId,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return analytics.overview(user.id(), new Filter(platform, accountId, startDate, endDate));
    }

    @GetMapping("/platforms")
    public List<Breakdown> platforms(@AuthenticationPrincipal AuthUser user,
                                     @RequestParam(required = false) Platform platform,
                                     @RequestParam(required = false) Long accountId,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return analytics.byPlatform(user.id(), new Filter(platform, accountId, startDate, endDate));
    }

    @GetMapping("/accounts")
    public List<Breakdown> accounts(@AuthenticationPrincipal AuthUser user,
                                    @RequestParam(required = false) Platform platform,
                                    @RequestParam(required = false) Long accountId,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return analytics.byAccount(user.id(), new Filter(platform, accountId, startDate, endDate));
    }

    @GetMapping("/trends")
    public List<TrendPoint> trends(@AuthenticationPrincipal AuthUser user,
                                   @RequestParam(required = false) Platform platform,
                                   @RequestParam(required = false) Long accountId,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return analytics.trends(user.id(), new Filter(platform, accountId, startDate, endDate));
    }

    @GetMapping("/posts")
    public PageResponse<PostPerformance> posts(@AuthenticationPrincipal AuthUser user,
                                               @RequestParam(required = false) Platform platform,
                                               @RequestParam(required = false) Long accountId,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                               @RequestParam(defaultValue = "engagement") String sort,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "10") int size) {
        return analytics.posts(user.id(), new Filter(platform, accountId, startDate, endDate), sort, page, size);
    }

    @GetMapping("/posts/{publicationId}/history")
    public List<PostHistoryPoint> postHistory(@AuthenticationPrincipal AuthUser user,
                                              @PathVariable Long publicationId) {
        return analytics.postHistory(user.id(), publicationId);
    }
}
