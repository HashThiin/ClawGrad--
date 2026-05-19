package com.openclaw.grading.service;

import com.openclaw.grading.config.OpenClawGatewayProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * OpenClaw Gateway 客户端服务（OpenAI 兼容模式）
 * 调用 POST /v1/chat/completions 与 AI 模型对话
 */
@Slf4j
@Service
public class OpenClawClientService {

    private final WebClient webClient;
    private final OpenClawGatewayProperties properties;

    public OpenClawClientService(OpenClawGatewayProperties properties) {
        this.properties = properties;

        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMillis(properties.getTimeout()));

        this.webClient = WebClient.builder()
                .baseUrl(properties.getUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Authorization", "Bearer " + properties.getToken())
                .defaultHeader("x-openclaw-agent-id", properties.getAgentId())
                .codecs(c -> c.defaultCodecs().maxInMemorySize(8 * 1024 * 1024))
                .build();
    }

    /**
     * 调用 Chat Completions API（OpenAI 兼容）
     *
     * @param model    模型名（例如 dashscope/glm-5）
     * @param systemPrompt 系统提示
     * @param userPrompt   用户消息
     * @return AI 回复的文本内容
     */
    public Mono<String> chatCompletion(String model, String systemPrompt, String userPrompt) {
        if (!properties.isEnabled()) {
            return Mono.error(new IllegalStateException("OpenClaw Gateway is disabled"));
        }

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                )
        );

        log.info("Calling OpenClaw chat completions: model={}", model);

        return webClient.post()
                .uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .map(this::extractContent)
                .doOnSuccess(text -> log.info("Chat completion success, content length={}", text.length()))
                .doOnError(err -> log.error("Chat completion failed: {}", err.getMessage()));
    }

    /**
     * 多模态 Chat Completions（OpenClaw 兼容格式）。
     * <p>userContentParts 例如：
     * <pre>
     * [
     *   {"type":"text","text":"请批改以下作业图片"},
     *   {"type":"image_url","image_url":{"url":"data:image/png;base64,..."}}
     * ]
     * </pre>
     */
    public Mono<String> chatCompletionParts(String model, String systemPrompt,
                                             List<Object> userContentParts) {
        if (!properties.isEnabled()) {
            return Mono.error(new IllegalStateException("OpenClaw Gateway is disabled"));
        }

        // 构建完整的消息内容：system prompt + user content parts
        List<Map<String, Object>> messages = new java.util.ArrayList<>();
        
        // System message（纯文本）
        messages.add(Map.of("role", "system", "content", systemPrompt));
        
        // User message（多模态 content array）
        messages.add(Map.of("role", "user", "content", userContentParts));

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", messages
        );

        log.info("Calling OpenClaw multimodal chat completions: model={}, parts={}",
                model, userContentParts.size());

        return webClient.post()
                .uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .map(this::extractContent)
                .doOnSuccess(text -> log.info("Multimodal chat completion success, content length={}", text.length()))
                .doOnError(err -> log.error("Multimodal chat completion failed: {}", err.getMessage()));
    }

    @SuppressWarnings("unchecked")
    private String extractContent(Map<?, ?> response) {
        Object choices = response.get("choices");
        if (!(choices instanceof List<?>) || ((List<?>) choices).isEmpty()) {
            throw new IllegalStateException("OpenClaw response missing choices: " + response);
        }
        Map<String, Object> first = (Map<String, Object>) ((List<?>) choices).get(0);
        Map<String, Object> message = (Map<String, Object>) first.get("message");
        if (message == null) {
            throw new IllegalStateException("OpenClaw response missing message: " + response);
        }
        Object content = message.get("content");
        return content == null ? "" : content.toString();
    }
}
