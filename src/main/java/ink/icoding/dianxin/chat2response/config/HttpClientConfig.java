package ink.icoding.dianxin.chat2response.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * HTTP客户端配置。
 */
@Configuration
public class HttpClientConfig {

    /**
     * 创建全局复用的 HTTP 客户端。
     *
     * @return HttpClient
     */
    @Bean
    public HttpClient httpClient() {
        return HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();
    }
}
