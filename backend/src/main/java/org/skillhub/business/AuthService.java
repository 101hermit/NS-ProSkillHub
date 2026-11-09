package org.skillhub.business;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.skillhub.common.Actor;
import org.skillhub.common.ApiException;
import org.skillhub.common.ErrorCode;
import org.skillhub.dto.AuthDtos;
import org.skillhub.mapper.AuthMapper;
import org.skillhub.model.AuthEntities;
import org.skillhub.port.ClockPort;
import org.skillhub.support.Dates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 登录认证：注册 / 登录 / 访客 / 退出 / 我的信息 / 改密码。
 *
 * <p>设计取舍（三系统一致）：
 * <ul>
 *   <li><b>密码</b>：PBKDF2WithHmacSHA256，16 字节随机盐，12 万次迭代。不引第三方库，JDK 自带。</li>
 *   <li><b>令牌</b>：32 位十六进制随机串，存库（可撤销），默认 7 天；请求头 {@code Authorization: Bearer …}。</li>
 *   <li><b>访客</b>：也能拿到一个令牌（role=viewer），这样"有没有登录"这件事在前后端只有一种处理路径。</li>
 *   <li><b>第一个注册的人是管理员</b>：内网小团队最省事的引导方式，之后注册的人默认 editor。</li>
 *   <li><b>不做的事</b>：不发邮件、不做验证码、不做第三方 OAuth —— 内网工具不需要，也别假装需要。</li>
 * </ul>
 *
 * <p><b>兼容性</b>：没有令牌时仍然沿用请求头 {@code X-SKH-Actor} / {@code X-SKH-Role}（"访客/演示"模式），
 * 因此老的调用方式与自动化脚本不受影响。
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final int ITERATIONS = 120_000;
    private static final int KEY_LENGTH = 256;
    private static final int SALT_BYTES = 16;
    private static final int TOKEN_BYTES = 16;
    private static final int TOKEN_TTL_DAYS = 7;
    private static final String GUEST = "guest";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuthMapper authMapper;
    private final OpLogService opLogService;
    private final ClockPort clock;

    public AuthService(AuthMapper authMapper, OpLogService opLogService, ClockPort clock) {
        this.authMapper = authMapper;
        this.opLogService = opLogService;
        this.clock = clock;
    }

    // ================================================================ 注册 / 登录

    /** 注册：第一个账号成为 admin，之后是 editor。 */
    @Transactional
    public AuthDtos.Session register(AuthDtos.RegisterRequest req) {
        String username = normalizeUsername(req == null ? null : req.getUsername());
        String password = req == null ? null : req.getPassword();
        validatePassword(password);
        if (authMapper.findByUsername(username) != null) {
            throw ApiException.of(ErrorCode.CONFLICT, "用户名「" + username + "」已被占用");
        }
        boolean first = authMapper.countAccounts() == 0;
        AuthEntities.UserAccount account = new AuthEntities.UserAccount();
        account.username = username;
        account.displayName = blank2(req.getDisplayName(), username);
        account.passwordSalt = newSalt();
        account.passwordHash = hash(password, account.passwordSalt);
        account.role = first ? "admin" : "editor";
        account.status = "active";
        authMapper.insertAccount(account);
        opLogService.record(new Actor(username, account.role), "auth.register", "user", username,
                first ? "首位注册用户，自动成为管理员" : "注册为编辑者");
        log.info("新用户注册：{}（{}）{}", username, account.role, first ? " [首用户=管理员]" : "");
        return issue(username, account.role, account.displayName, false);
    }

    /** 登录。 */
    @Transactional
    public AuthDtos.Session login(AuthDtos.LoginRequest req) {
        String username = normalizeUsername(req == null ? null : req.getUsername());
        String password = req == null ? null : req.getPassword();
        if (password == null || password.isEmpty()) {
            throw ApiException.of(ErrorCode.MISSING_PARAM, "password");
        }
        AuthEntities.UserAccount account = authMapper.findByUsername(username);
        // 统一话术：不区分"用户不存在"与"密码错"，避免被用来枚举账号
        if (account == null || !hash(password, account.passwordSalt).equals(account.passwordHash)) {
            throw ApiException.ofMessage(ErrorCode.ROLE_FORBIDDEN,
                    "用户名或密码不对。怎么办：检查大小写；忘了密码可以重新注册一个账号，或让管理员在数据库里重置。");
        }
        if (!"active".equals(account.status)) {
            throw ApiException.ofMessage(ErrorCode.ROLE_FORBIDDEN,
                    "这个账号已被停用。怎么办：联系管理员恢复。");
        }
        authMapper.touchLogin(username);
        authMapper.purgeExpired();
        opLogService.record(new Actor(username, account.role), "auth.login", "user", username, "");
        return issue(username, account.role, account.displayName, false);
    }

    /** 访客：发一个 viewer 令牌（不落账号表）。 */
    @Transactional
    public AuthDtos.Session guest() {
        authMapper.purgeExpired();
        return issue(GUEST, "viewer", "访客", true);
    }

    /** 退出：删掉这个令牌（其它设备上的会话不受影响）。 */
    @Transactional
    public void logout(String token) {
        if (token != null && !token.isBlank() && !GUEST.equals(token)) {
            authMapper.deleteToken(token.trim());
        }
    }

    // ================================================================ 查询

    /** 令牌 → 身份；无效或过期返回 {@code null}。 */
    public Actor resolve(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        AuthEntities.AuthToken row = authMapper.findToken(token.trim());
        if (row == null) {
            return null;
        }
        if (row.expiresAt != null && row.expiresAt.isBefore(clock.now())) {
            authMapper.deleteToken(row.token);
            return null;
        }
        return new Actor(row.username, row.role);
    }

    /** 当前身份的公开档案。 */
    public AuthDtos.Profile profile(Actor actor) {
        if (actor == null) {
            return guestProfile();
        }
        AuthEntities.UserAccount account = authMapper.findByUsername(actor.name());
        if (account == null) {
            return new AuthDtos.Profile(0, actor.name(), "访客", actor.role(),
                    roleLabel(actor.role()), "guest", "", "");
        }
        return toProfile(account);
    }

    /** 改密码（需旧密码）。 */
    @Transactional
    public void changePassword(Actor actor, AuthDtos.PasswordRequest req) {
        if (actor == null || GUEST.equals(actor.name())) {
            throw ApiException.ofMessage(ErrorCode.ROLE_FORBIDDEN,
                    "访客不能改密码。怎么办：先注册或登录一个账号。");
        }
        AuthEntities.UserAccount account = authMapper.findByUsername(actor.name());
        if (account == null) {
            throw ApiException.of(ErrorCode.NOT_FOUND, "账号「" + actor.name() + "」");
        }
        if (!hash(req.getOldPassword(), account.passwordSalt).equals(account.passwordHash)) {
            throw ApiException.ofMessage(ErrorCode.ROLE_FORBIDDEN, "旧密码不对。怎么办：重新输入。");
        }
        validatePassword(req.getNewPassword());
        String salt = newSalt();
        authMapper.updatePassword(account.username, hash(req.getNewPassword(), salt), salt);
        authMapper.deleteTokensOf(account.username); // 改密后其它会话失效
        opLogService.record(actor, "auth.password", "user", account.username, "已改密码并撤销旧会话");
    }

    // ================================================================ 内部

    private AuthDtos.Session issue(String username, String role, String displayName, boolean guest) {
        AuthEntities.AuthToken row = new AuthEntities.AuthToken();
        row.token = newToken();
        row.username = username;
        row.role = role;
        row.expiresAt = clock.now().plusDays(TOKEN_TTL_DAYS);
        authMapper.insertToken(row);
        return new AuthDtos.Session(row.token, username, blank2(displayName, username), role,
                roleLabel(role), Dates.format(row.expiresAt), guest, permissionsOf(role));
    }

    private static AuthDtos.Profile guestProfile() {
        return new AuthDtos.Profile(0, GUEST, "访客", "viewer", roleLabel("viewer"), "guest", "", "");
    }

    private static AuthDtos.Profile toProfile(AuthEntities.UserAccount a) {
        return new AuthDtos.Profile(a.id == null ? 0 : a.id, a.username,
                blank2(a.displayName, a.username), a.role, roleLabel(a.role), a.status,
                Dates.format(a.createdAt), Dates.format(a.lastLoginAt));
    }

    /** 角色对应的高层权限（前端据此灰掉按钮；真正的判定仍在业务层）。 */
    public static List<String> permissionsOf(String role) {
        Set<String> out = new LinkedHashSet<>();
        out.add("read");
        if ("editor".equals(role) || "admin".equals(role)) {
            out.addAll(List.of("skill.write", "contribution.publish", "eval.run"));
        }
        if ("admin".equals(role)) {
            out.addAll(List.of("ms.admin", "skill.delete.hard", "auth.admin"));
        }
        return List.copyOf(out);
    }

    public static String roleLabel(String role) {
        return switch (role == null ? "" : role) {
            case "admin" -> "管理员";
            case "editor" -> "编辑者";
            default -> "访客（只读）";
        };
    }

    private static String normalizeUsername(String raw) {
        String u = raw == null ? "" : raw.trim().toLowerCase(java.util.Locale.ROOT);
        if (u.isEmpty()) {
            throw ApiException.of(ErrorCode.MISSING_PARAM, "username");
        }
        if (!u.matches("[a-z0-9][a-z0-9_.-]{2,31}")) {
            throw ApiException.ofMessage(ErrorCode.INVALID_PARAM,
                    "用户名只能用 3-32 位小写字母、数字、下划线、点或连字符，且以字母或数字开头。怎么办：例如 zhangsan、li.si-01。");
        }
        return u;
    }

    private static void validatePassword(String password) {
        if (password == null || password.length() < 8) {
            throw ApiException.ofMessage(ErrorCode.INVALID_PARAM,
                    "密码至少 8 位。怎么办：用一句自己能记住的话，例如 Skillhub2026。");
        }
    }

    private static String newSalt() {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    private static String newToken() {
        byte[] buf = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(buf);
        StringBuilder sb = new StringBuilder(32);
        for (byte b : buf) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /** PBKDF2WithHmacSHA256，返回 Base64。 */
    static String hash(String password, String saltB64) {
        try {
            byte[] salt = Base64.getDecoder().decode(saltB64);
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = factory.generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            // 理论上不会发生（JDK 17 必带 PBKDF2）；真发生了也不能静默降级成明文
            throw new IllegalStateException("密码哈希失败：" + e.getMessage(), e);
        }
    }

    /** 给单测用：常数时间比较（避免长度/前缀泄露）。 */
    static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static String blank2(String v, String fallback) {
        return v == null || v.isBlank() ? fallback : v.trim();
    }

    /** 供健康检查展示（不泄露任何账号信息）。 */
    public long accountCount() {
        try {
            return authMapper.countAccounts();
        } catch (RuntimeException e) {
            return 0;
        }
    }
}
