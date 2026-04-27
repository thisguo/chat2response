package ink.icoding.dianxin.chat2response.service;

import ink.icoding.dianxin.chat2response.auth.UnauthorizedException;
import ink.icoding.dianxin.chat2response.vo.AdminLoginVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理后台认证服务。
 */
@Service
public class AdminAuthService {

    private final String adminUsername;
    private final String adminPassword;

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();

    private static final long EXPIRE_SECONDS = 24 * 60 * 60;

    public AdminAuthService(
        @Value("${management.user.username:admin}") String adminUsername,
        @Value("${management.user.password:admin123456}") String adminPassword
    ) {
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    /**
     * 执行登录。
     *
     * @param username 用户名
     * @param password 密码
     * @return 登录结果
     */
    public AdminLoginVO login(String username, String password) {
        if (!adminUsername.equals(username) || !adminPassword.equals(password)) {
            throw new UnauthorizedException("用户名或密码错误");
        }

        String token = generateToken();
        long expiresAt = Instant.now().getEpochSecond() + EXPIRE_SECONDS;
        sessions.put(token, new Session(adminUsername, expiresAt));

        AdminLoginVO vo = new AdminLoginVO();
        vo.setToken(token);
        vo.setTokenType("Bearer");
        vo.setExpiresAt(expiresAt);
        vo.setUsername(adminUsername);
        return vo;
    }

    /**
     * 检查并获取当前会话用户名。
     *
     * @param authorization Authorization头
     * @return 用户名
     */
    public String verifyAndGetUsername(String authorization) {
        String token = extractToken(authorization);
        Session session = sessions.get(token);
        if (session == null) {
            throw new UnauthorizedException("登录已失效, 请重新登录");
        }
        if (session.expiresAt < Instant.now().getEpochSecond()) {
            sessions.remove(token);
            throw new UnauthorizedException("登录已过期, 请重新登录");
        }
        return session.username;
    }

    /**
     * 执行登出。
     *
     * @param authorization Authorization头
     */
    public void logout(String authorization) {
        String token = extractToken(authorization);
        sessions.remove(token);
    }

    private String extractToken(String authorization) {
        if (authorization == null || authorization.isBlank() || !authorization.startsWith("Bearer ")) {
            throw new UnauthorizedException("缺少有效的Authorization Bearer Token");
        }
        String token = authorization.substring("Bearer ".length()).trim();
        if (token.isBlank()) {
            throw new UnauthorizedException("Token不能为空");
        }
        return token;
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private record Session(String username, long expiresAt) {
    }
}
