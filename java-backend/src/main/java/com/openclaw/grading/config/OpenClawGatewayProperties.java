package com.openclaw.grading.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * OpenClaw Gateway 配置属性
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "openclaw.gateway")
public class OpenClawGatewayProperties {
    
    /**
     * Gateway URL
     */
    private String url = "http://127.0.0.1:18789";
    
    /**
     * Gateway Token
     */
    private String token;
    
    /**
     * 请求超时时间（毫秒）
     */
    private long timeout = 30000;
    
    /**
     * 是否启用
     */
    private boolean enabled = true;
    
    /**
     * 回调URL（OpenClaw批改完成后通知我们的地址）
     */
    private String callbackUrl = "http://localhost:8080/api/v1/callback/grading";

    /**
     * 默认调用的模型id（与 OpenClaw 路由匹配，例如 dashscope/glm-5）
     */
    private String model = "dashscope/glm-5";

    /**
     * OpenClaw Agent ID（用于路由和会话管理）
     */
    private String agentId = "main";
}
