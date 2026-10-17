package com.socialdash.listening;

import com.socialdash.account.Platform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ListeningDtos {

    private ListeningDtos() {}

    public record AddKeywordRequest(
            @NotBlank @Size(min = 2, max = 100)
            @Pattern(regexp = "^#?[\\p{L}\\p{N}_ .\\-]+$", message = "letters, numbers, spaces, _ . - and a leading #")
            String keyword,
            Platform platform) {}

    public record KeywordResponse(Long id, String keyword, Platform platform, Instant createdAt,
                                  long mentionsLast24h, long mentionsLast7d) {}

    public record MentionResponse(Long id, Long keywordId, String keyword, Platform platform, String authorHandle,
                                  String content, String url, Instant detectedAt) {

        public static MentionResponse from(SocialMention m) {
            return new MentionResponse(m.getId(), m.getTrackedKeyword().getId(), m.getTrackedKeyword().getKeyword(),
                    m.getPlatform(), m.getAuthorHandle(), m.getContent(), m.getUrl(), m.getDetectedAt());
        }
    }

    /** previousTotal is null when tracking didn't cover the whole previous period. */
    public record VolumeSeries(Long keywordId, String keyword, List<Long> counts, long total, Long previousTotal) {}

    public record VolumeResponse(List<LocalDate> dates, List<VolumeSeries> series) {}

    public record TermCount(String term, long count) {}
}
