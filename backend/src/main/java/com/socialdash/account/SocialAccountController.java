package com.socialdash.account;

import com.socialdash.account.AccountDtos.AccountResponse;
import com.socialdash.account.AccountDtos.ConnectAccountRequest;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts")
public class SocialAccountController {

    private final SocialAccountService accountService;

    public SocialAccountController(SocialAccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public List<AccountResponse> list(@AuthenticationPrincipal AuthUser user) {
        return accountService.list(user.id());
    }

    @PostMapping("/simulated")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse connectSimulated(@AuthenticationPrincipal AuthUser user,
                                            @Valid @RequestBody ConnectAccountRequest req) {
        return accountService.connectSimulated(user.id(), req);
    }

    @PostMapping("/{id}/sync")
    public AccountResponse sync(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return accountService.sync(user.id(), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        accountService.disconnect(user.id(), id);
    }
}
