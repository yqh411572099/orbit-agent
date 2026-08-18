package com.butler.perception.api;

import com.butler.application.UserAppService;
import com.butler.domain.model.User;
import com.butler.infrastructure.auth.CurrentUser;
import com.butler.infrastructure.auth.SessionTokenService;
import com.butler.perception.api.dto.SessionView;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/session")
public class SessionController {

    private final SessionTokenService tokenService;
    private final UserAppService userAppService;

    public SessionController(SessionTokenService tokenService, UserAppService userAppService) {
        this.tokenService = tokenService;
        this.userAppService = userAppService;
    }

    /** 用当前未过期 token 换发新 token（页面保持登录时定时调用）。 */
    @PostMapping("/refresh")
    public SessionView refresh() {
        SessionTokenService.SessionPayload p = CurrentUser.get();
        if (p == null) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.UNAUTHORIZED, "未登录");
        return sessionView(userAppService.user(p.userId())
                .orElseThrow(() -> new IllegalArgumentException("用户不存在")));
    }

    @PostMapping("/logout")
    public Map<String, Object> logout() {
        // 无状态 token：登出由前端丢弃；这里仅返回成功
        return Map.of("ok", true);
    }

    private SessionView sessionView(User u) {
        String token = tokenService.issue(new SessionTokenService.SessionPayload(
                u.getId(), u.getUsername(), u.getNickname(),
                u.getUserType().name(), u.getUserType().getLabel(), null, null));
        SessionTokenService.SessionPayload validated = tokenService.validate(token);
        return new SessionView(token, validated.expiresAt().toString(),
                new UserController.UserView(u.getId(), u.getUsername(), u.getNickname(),
                        u.getUserType().name(), u.getUserType().getLabel()));
    }
}
