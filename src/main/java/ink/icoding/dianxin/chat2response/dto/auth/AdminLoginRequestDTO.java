package ink.icoding.dianxin.chat2response.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 管理员登录请求体。
 */
public class AdminLoginRequestDTO {

    /**
     * 管理员用户名。
     */
    @NotBlank(message = "username不能为空")
    @Size(max = 64, message = "username长度不能超过64")
    private String username;

    /**
     * 管理员密码。
     */
    @NotBlank(message = "password不能为空")
    @Size(max = 128, message = "password长度不能超过128")
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
