package com.socialdash.listening;

import com.socialdash.account.Platform;
import com.socialdash.common.PageResponse;
import com.socialdash.listening.ListeningDtos.AddKeywordRequest;
import com.socialdash.listening.ListeningDtos.KeywordResponse;
import com.socialdash.listening.ListeningDtos.MentionResponse;
import com.socialdash.listening.ListeningDtos.TermCount;
import com.socialdash.listening.ListeningDtos.VolumeResponse;
import com.socialdash.security.AuthUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/listening")
public class ListeningController {

    private final ListeningService listening;

    public ListeningController(ListeningService listening) {
        this.listening = listening;
    }

    @GetMapping("/keywords")
    public List<KeywordResponse> keywords(@AuthenticationPrincipal AuthUser user) {
        return listening.keywords(user.id());
    }

    @PostMapping("/keywords")
    @ResponseStatus(HttpStatus.CREATED)
    public KeywordResponse addKeyword(@AuthenticationPrincipal AuthUser user,
                                      @Valid @RequestBody AddKeywordRequest req) {
        return listening.addKeyword(user.id(), req);
    }

    @DeleteMapping("/keywords/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteKeyword(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        listening.deleteKeyword(user.id(), id);
    }

    @GetMapping("/mentions")
    public PageResponse<MentionResponse> mentions(@AuthenticationPrincipal AuthUser user,
                                                  @RequestParam(required = false) Long keywordId,
                                                  @RequestParam(required = false) Platform platform,
                                                  @RequestParam(required = false) String q,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return listening.mentions(user.id(), keywordId, platform, q, page, size);
    }

    @GetMapping("/volume")
    public VolumeResponse volume(@AuthenticationPrincipal AuthUser user,
                                 @RequestParam(required = false) Long keywordId,
                                 @RequestParam(defaultValue = "14") int days) {
        return listening.volume(user.id(), keywordId, days);
    }

    @GetMapping("/top-terms")
    public List<TermCount> topTerms(@AuthenticationPrincipal AuthUser user,
                                    @RequestParam(required = false) Long keywordId,
                                    @RequestParam(defaultValue = "14") int days) {
        return listening.topTerms(user.id(), keywordId, days);
    }
}
