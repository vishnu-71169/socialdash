package com.socialdash.post;

import com.socialdash.common.PageResponse;
import com.socialdash.post.PostDtos.PostRequest;
import com.socialdash.post.PostDtos.PostResponse;
import com.socialdash.post.PostDtos.PostSummary;
import com.socialdash.security.AuthUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @GetMapping
    public PageResponse<PostResponse> list(@AuthenticationPrincipal AuthUser user,
                                           @RequestParam(required = false) List<PostStatus> status,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "10") int size) {
        return postService.list(user.id(), status, page, size);
    }

    @GetMapping("/summary")
    public PostSummary summary(@AuthenticationPrincipal AuthUser user) {
        return postService.summary(user.id());
    }

    @GetMapping("/{id}")
    public PostResponse get(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return postService.get(user.id(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PostResponse create(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody PostRequest req) {
        return postService.create(user.id(), req);
    }

    @PutMapping("/{id}")
    public PostResponse update(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                               @Valid @RequestBody PostRequest req) {
        return postService.update(user.id(), id, req);
    }

    @PostMapping("/{id}/cancel")
    public PostResponse cancel(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return postService.cancel(user.id(), id);
    }

    @PostMapping("/{id}/retry")
    public PostResponse retry(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return postService.retryFailed(user.id(), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        postService.delete(user.id(), id);
    }
}
