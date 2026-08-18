package com.butler.perception.api;

import com.butler.application.UserAppService;
import com.butler.domain.model.User;
import com.butler.domain.model.UserType;
import com.butler.infrastructure.auth.SessionTokenService;
import com.butler.perception.api.dto.SessionView;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserAppService userAppService;
    private final SessionTokenService tokenService;

    public UserController(UserAppService userAppService, SessionTokenService tokenService) {
        this.userAppService = userAppService;
        this.tokenService = tokenService;
    }

    public record AuthRequest(String username, String password, String nickname) {}
    public record UserView(Long id, String username, String nickname, String userType, String userTypeLabel) {}

    @PostMapping("/register")
    public SessionView register(@RequestBody AuthRequest req) {
        try {
            User u = userAppService.register(req.username(), req.password(), req.nickname(), UserType.NORMAL);
            return toSession(u);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/login")
    public SessionView login(@RequestBody AuthRequest req) {
        try {
            return toSession(userAppService.login(req.username(), req.password()));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public UserView get(@PathVariable Long id) {
        return userAppService.user(id).map(this::toView)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + id));
    }

    private SessionView toSession(User u) {
        String token = tokenService.issue(new SessionTokenService.SessionPayload(
                u.getId(), u.getUsername(), u.getNickname(),
                u.getUserType().name(), u.getUserType().getLabel(), null, null));
        SessionTokenService.SessionPayload p = tokenService.validate(token);
        return new SessionView(token, p.expiresAt().toString(), toView(u));
    }

    private UserView toView(User u) {
        return new UserView(u.getId(), u.getUsername(), u.getNickname(),
                u.getUserType().name(), u.getUserType().getLabel());
    }
}
