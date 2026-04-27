package ink.icoding.dianxin.chat2response.vo;

/**
 * 管理员登录返回体。
 */
public class AdminLoginVO {

    /**
     * 登录令牌, 前端需放在 Authorization 请求头中。
     */
    private String token;

    /**
     * 令牌类型, 固定 Bearer。
     */
    private String tokenType;

    /**
     * 过期时间戳(秒)。
     */
    private long expiresAt;

    /**
     * 登录用户名。
     */
    private String username;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(long expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
