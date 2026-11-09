package org.skillhub.dto;

import java.util.List;

/** 登录认证的对外形状。字段集合三系统一致。 */
public final class AuthDtos {

    private AuthDtos() {
    }

    /** 账号公开信息（永远不含密码）。 */
    public record Profile(long id, String username, String displayName, String role,
                          String roleLabel, String status, String createdAt, String lastLoginAt) {
    }

    /** 登录 / 注册 / 访客成功后返回。 */
    public record Session(String token, String username, String displayName, String role,
                          String roleLabel, String expiresAt, boolean guest, List<String> permissions) {
    }

    /** 注册请求。 */
    public static class RegisterRequest {
        private String username;
        private String password;
        private String displayName;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }
    }

    /** 登录请求。 */
    public static class LoginRequest {
        private String username;
        private String password;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    /** 改密码请求（可选功能，给「我的账号」用）。 */
    public static class PasswordRequest {
        private String oldPassword;
        private String newPassword;

        public String getOldPassword() {
            return oldPassword;
        }

        public void setOldPassword(String oldPassword) {
            this.oldPassword = oldPassword;
        }

        public String getNewPassword() {
            return newPassword;
        }

        public void setNewPassword(String newPassword) {
            this.newPassword = newPassword;
        }
    }
}
