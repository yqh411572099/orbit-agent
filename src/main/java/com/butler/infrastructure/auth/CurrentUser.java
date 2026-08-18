package com.butler.infrastructure.auth;

/** 请求上下文中的当前登录用户（由 AuthFilter 写入）。 */
public class CurrentUser {
    private static final ThreadLocal<SessionTokenService.SessionPayload> HOLDER = new ThreadLocal<>();

    public static void set(SessionTokenService.SessionPayload p) { HOLDER.set(p); }
    public static SessionTokenService.SessionPayload get() { return HOLDER.get(); }
    public static Long userId() {
        SessionTokenService.SessionPayload p = HOLDER.get();
        return p == null ? null : p.userId();
    }
    public static void clear() { HOLDER.remove(); }
}
