package com.socialdash.user;

import com.socialdash.common.ApiException;
import com.socialdash.security.JwtService;
import com.socialdash.user.UserDtos.AuthResponse;
import com.socialdash.user.UserDtos.ChangePasswordRequest;
import com.socialdash.user.UserDtos.LoginRequest;
import com.socialdash.user.UserDtos.RegisterRequest;
import com.socialdash.user.UserDtos.UpdateProfileRequest;
import com.socialdash.user.UserDtos.UserResponse;
import java.util.Locale;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public UserService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("An account with this email already exists");
        }
        User user = users.save(new User(req.name().trim(), email, passwordEncoder.encode(req.password())));
        return new AuthResponse(jwtService.issue(user.getId(), user.getEmail()), UserResponse.from(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        User user = users.findByEmailIgnoreCase(req.email().trim())
                .filter(u -> passwordEncoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException("bad credentials"));
        return new AuthResponse(jwtService.issue(user.getId(), user.getEmail()), UserResponse.from(user));
    }

    @Transactional(readOnly = true)
    public UserResponse me(Long userId) {
        return UserResponse.from(get(userId));
    }

    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest req) {
        User user = get(userId);
        user.setName(req.name().trim());
        return UserResponse.from(user);
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest req) {
        User user = get(userId);
        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
    }

    public User get(Long userId) {
        return users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
    }
}
