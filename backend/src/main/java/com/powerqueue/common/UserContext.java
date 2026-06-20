package com.powerqueue.common;

/**
 * 当前登录用户上下文(基于 ThreadLocal,由 JWT 拦截器写入)。
 */
public class UserContext {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    public static CurrentUser get() {
        return HOLDER.get();
    }

    public static Long getUserId() {
        CurrentUser u = HOLDER.get();
        return u == null ? null : u.userId();
    }

    public static String getRole() {
        CurrentUser u = HOLDER.get();
        return u == null ? null : u.role();
    }

    public static void clear() {
        HOLDER.remove();
    }

    /**
     * 当前登录用户信息。
     */
    public record CurrentUser(Long userId, String username, String role) {
    }
}
