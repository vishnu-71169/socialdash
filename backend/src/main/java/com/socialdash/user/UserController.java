package com.socialdash.user;

import com.socialdash.security.AuthUser;
import com.socialdash.user.UserDtos.AuthResponse;
import com.socialdash.user.UserDtos.ChangePasswordRequest;
import com.socialdash.user.UserDtos.LoginRequest;
import com.socialdash.user.UserDtos.RegisterRequest;
import com.socialdash.user.UserDtos.UpdateProfileRequest;
import com.socialdash.user.UserDtos.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        return userService.register(req);
    }

    @PostMapping("/auth/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return userService.login(req);
    }

    @GetMapping("/users/me")
    public UserResponse me(@AuthenticationPrincipal AuthUser user) {
        return userService.me(user.id());
    }

    @PutMapping("/users/me")
    public UserResponse updateProfile(@AuthenticationPrincipal AuthUser user,
                                      @Valid @RequestBody UpdateProfileRequest req) {
        return userService.updateProfile(user.id(), req);
    }

    @PutMapping("/users/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal AuthUser user,
                               @Valid @RequestBody ChangePasswordRequest req) {
        userService.changePassword(user.id(), req);
    }
}
