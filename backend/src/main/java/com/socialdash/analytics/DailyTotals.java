package com.socialdash.analytics;

import java.time.LocalDate;

/** Sum of daily metrics across a set of accounts for one date. */
public record DailyTotals(LocalDate date, Long followers, Long impressions, Long likes, Long comments, Long shares) {}
