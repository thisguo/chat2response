package ink.icoding.dianxin.chat2response.auth;

/**
 * 未授权异常, 表示请求未登录或登录态失效。
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
