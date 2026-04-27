package ink.icoding.dianxin.chat2response.controller.gateway;

import ink.icoding.dianxin.chat2response.service.ResponseGatewayService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * 对外兼容 Responses API 的网关接口。
 */
@RestController
@RequestMapping("/v1")
public class ResponsesController {

    private final ResponseGatewayService responseGatewayService;

    public ResponsesController(ResponseGatewayService responseGatewayService) {
        this.responseGatewayService = responseGatewayService;
    }

    /**
     * Responses API create 调用入口。支持 stream=true 返回 SSE 事件流。
     *
     * @param authorization Bearer token
     * @param requestBody   Responses 风格请求体
     * @return Responses 风格响应体（Map）或 SseEmitter（流式）
     */
    @PostMapping("/responses")
    public Object createResponse(
        @RequestHeader("Authorization") String authorization,
        @RequestBody Map<String, Object> requestBody
    ) {
        if (Boolean.TRUE.equals(requestBody.get("stream"))) {
            return responseGatewayService.createStreamingResponse(authorization, requestBody);
        }
        return responseGatewayService.createResponse(authorization, requestBody);
    }
}
