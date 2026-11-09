package org.skillhub.model;

import java.time.LocalDateTime;

/**
 * 登录认证的数据库实体（与 {@code db/schema.sql} 的 auth 段一一对应）。
 *
 * <p>密码只存 <b>PBKDF2WithHmacSHA256 加盐哈希</b>（12 万次迭代），不存明文也不存可逆密文；
 * 令牌是随机 32 位十六进制串，服务端可随时撤销（删行即失效）。
 */
public final class AuthEntities {

    private AuthEntities() {
    }

    /// 账号。{@code role} ∈ viewer / editor / admin。
    public static class UserAccount {
        public Long id;
        public String username;
        public String displayName;
        public String passwordHash;
        public String passwordSalt;
        public String role;
        public String status;
        public LocalDateTime lastLoginAt;
        public LocalDateTime createdAt;
    }

    /// 会话令牌。
    public static class AuthToken {
        public Long id;
        public String token;
        public String username;
        public String role;
        public LocalDateTime expiresAt;
        public LocalDateTime createdAt;
    }
}
