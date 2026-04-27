package ink.icoding.dianxin.chat2response.controller.admin;

import ink.icoding.dianxin.chat2response.dto.auth.AdminLoginRequestDTO;
import ink.icoding.dianxin.chat2response.service.AdminAuthService;
import ink.icoding.dianxin.chat2response.vo.AdminLoginVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理后台登录认证接口。
 */
@RestController
@RequestMapping("/admin/auth")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    public AdminAuthController(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    /**
     * 管理员登录。
     *
     * @param dto 登录请求
     * @return 登录结果
     */
    @PostMapping("/login")
    public AdminLoginVO login(@Valid @RequestBody AdminLoginRequestDTO dto) {
        return adminAuthService.login(dto.getUsername(), dto.getPassword());
    }

    /**
     * 获取当前登录信息。
     *
     * @param authorization Bearer Token
     * @return 当前用户信息
     */
    @GetMapping("/me")
    public Map<String, Object> me(@RequestHeader("Authorization") String authorization) {
        String username = adminAuthService.verifyAndGetUsername(authorization);
        return Map.of("username", username);
    }

    /**
     * 登出。
     *
     * @param authorization Bearer Token
     */
    @PostMapping("/logout")
    public void logout(@RequestHeader("Authorization") String authorization) {
        adminAuthService.logout(authorization);
    }
}
