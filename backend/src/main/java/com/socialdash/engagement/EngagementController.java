package com.socialdash.engagement;

import com.socialdash.account.Platform;
import com.socialdash.common.PageResponse;
import com.socialdash.engagement.EngagementDtos.EngagementResponse;
import com.socialdash.engagement.EngagementDtos.EngagementSummary;
import com.socialdash.engagement.EngagementDtos.ReplyRequest;
import com.socialdash.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/engagements")
public class EngagementController {

    private final EngagementService engagementService;

    public EngagementController(EngagementService engagementService) {
        this.engagementService = engagementService;
    }

    @GetMapping
    public PageResponse<EngagementResponse> list(@AuthenticationPrincipal AuthUser user,
                                                 @RequestParam(required = false) Engagement.Status status,
                                                 @RequestParam(required = false) Engagement.Type type,
                                                 @RequestParam(required = false) Platform platform,
                                                 @RequestParam(required = false) Long accountId,
                                                 @RequestParam(required = false) String q,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return engagementService.list(user.id(),
                new EngagementService.Filter(status, type, platform, accountId, q), page, size);
    }

    @GetMapping("/summary")
    public EngagementSummary summary(@AuthenticationPrincipal AuthUser user) {
        return engagementService.summary(user.id());
    }

    @PostMapping("/{id}/reply")
    public EngagementResponse reply(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                    @Valid @RequestBody ReplyRequest req) {
        return engagementService.reply(user.id(), id, req.message());
    }

    @PostMapping("/{id}/resolve")
    public EngagementResponse resolve(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return engagementService.resolve(user.id(), id);
    }

    @PostMapping("/{id}/reopen")
    public EngagementResponse reopen(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return engagementService.reopen(user.id(), id);
    }
}
