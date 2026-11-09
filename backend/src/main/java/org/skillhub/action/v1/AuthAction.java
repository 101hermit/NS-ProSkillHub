package org.skillhub.action.v1;

import org.skillhub.business.AuthService;
import org.skillhub.common.ActorContext;
import org.skillhub.common.ApiResult;
import org.skillhub.dto.AuthDtos;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录认证端点：注册 / 登录 / 访客 / 退出 / 我的信息 / 改密码。
 *
 * <p>令牌走 {@code Authorization: Bearer <token>}；不带令牌时沿用
 * {@code X-SKH-Actor} / {@code X-SKH-Role} 的"演示/访客"模式（向后兼容）。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthAction {

    private final AuthService authService;
    private final ActorContext actorContext;

    public AuthAction(AuthService authService, ActorContext actorContext) {
        this.authService = authService;
        this.actorContext = actorContext;
    }

    @PostMapping("/register")
    public ApiResult<AuthDtos.Session> register(@RequestBody AuthDtos.RegisterRequest request) {
        return ApiResult.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResult<AuthDtos.Session> login(@RequestBody AuthDtos.LoginRequest request) {
        return ApiResult.ok(authService.login(request));
    }

    /** 访客：拿到一个只读令牌，体验完整界面但不给写权限。 */
    @PostMapping("/guest")
    public ApiResult<AuthDtos.Session> guest() {
        return ApiResult.ok(authService.guest());
    }

    @PostMapping("/logout")
    public ApiResult<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.logout(bearer(authorization));
        return ApiResult.ok();
    }

    @GetMapping("/me")
    public ApiResult<AuthDtos.Profile> me() {
        return ApiResult.ok(authService.profile(actorContext.current()));
    }

    @PostMapping("/password")
    public ApiResult<Void> changePassword(@RequestBody AuthDtos.PasswordRequest request) {
        authService.changePassword(actorContext.current(), request);
        return ApiResult.ok();
    }

    /** 从 {@code "Bearer xxx"} 里取出令牌。 */
    public static String bearer(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        String a = authorization.trim();
        if (a.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return a.substring(7).trim();
        }
        return a;
    }
}
