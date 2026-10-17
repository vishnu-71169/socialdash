package com.socialdash.analytics;

/** Sum of engagement metrics for one account over a date range. */
public record AccountTotals(Long accountId, Long impressions, Long likes, Long comments, Long shares) {}
