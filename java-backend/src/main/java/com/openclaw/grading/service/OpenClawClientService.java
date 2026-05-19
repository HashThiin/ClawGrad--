package com.openclaw.grading.service;

import com.openclaw.grading.config.OpenClawGatewayProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenClaw Gateway 客户端服务（OpenAI 兼容模式）
 * <p>按照 OpenClaw 官方推荐方式调用 POST /v1/chat/completions
 * <p>请求头：Authorization: Bearer {token}
 * <p>请求体：{"model": "bailian-token-plan/xxx", "messages": [...]}
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

        // 按 OpenClaw 推荐：仅需 Authorization 头，不要 x-openclaw-agent-id（会导致模型锁定）
        this.webClient = WebClient.builder()
                .baseUrl(properties.getUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Authorization", "Bearer " + properties.getToken())
                .defaultHeader("Content-Type", "application/json")
                .codecs(c -> c.defaultCodecs().maxInMemorySize(8 * 1024 * 1024))
                .build();
    }

    /**
     * 调用 Chat Completions API（纯文本，OpenAI 兼容）
     *
     * @param model        完整模型ID（如 bailian-token-plan/qwen3.6-plus）
     * @param systemPrompt 系统提示
     * @param userPrompt   用户消息
     * @return AI 回复的文本内容
     */
    public Mono<String> chatCompletion(String model, String systemPrompt, String userPrompt) {
        if (!properties.isEnabled()) {
            return Mono.error(new IllegalStateException("OpenClaw Gateway is disabled"));
        }

        // 构建 OpenAI 兼容请求体
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);

        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> sysMsg = new LinkedHashMap<>();
        sysMsg.put("role", "system");
        sysMsg.put("content", systemPrompt);
        messages.add(sysMsg);

        Map<String, Object> userMsg = new LinkedHashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userPrompt);
        messages.add(userMsg);

        body.put("messages", messages);

        String bodyJson;
        try {
            bodyJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(body);
        } catch (Exception e) {
            bodyJson = body.toString();
        }
        log.info("[OpenClaw] 调用模型: model={}", model);
        log.info("[OpenClaw] 请求体: {}", bodyJson);

        return webClient.post()
                .uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .map(this::extractContent)
                .doOnSuccess(text -> log.info("[OpenClaw] 成功: model={}, responseLen={}", model, text.length()))
                .doOnError(WebClientResponseException.class, err ->
                        log.error("[OpenClaw] HTTP错误: model={}, status={}, body={}",
                                model, err.getStatusCode(), err.getResponseBodyAsString()))
                .doOnError(err -> {
                    if (!(err instanceof WebClientResponseException)) {
                        log.error("[OpenClaw] 调用失败: model={}, error={}", model, err.getMessage());
                    }
                });
    }

    /**
     * 多模态 Chat Completions（支持图片，OpenClaw 兼容格式）。
     * <p>userContentParts 为 OpenAI 兼容的 content 数组：
     * <pre>
     * [
     *   {"type":"text","text":"请批改以下作业图片"},
     *   {"type":"image_url","image_url":{"url":"data:image/png;base64,..."}}
     * ]
     * </pre>
     *
     * @param model            完整模型ID（如 bailian-token-plan/qwen3.6-plus）
     * @param systemPrompt     系统提示
     * @param userContentParts 多模态内容片段
     * @return AI 回复的文本内容
     */
    public Mono<String> chatCompletionParts(String model, String systemPrompt,
                                             List<Object> userContentParts) {
        if (!properties.isEnabled()) {
            return Mono.error(new IllegalStateException("OpenClaw Gateway is disabled"));
        }

        // 构建 OpenAI 兼容请求体（多模态）
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);

        List<Map<String, Object>> messages = new ArrayList<>();

        // System message（纯文本）
        Map<String, Object> sysMsg = new LinkedHashMap<>();
        sysMsg.put("role", "system");
        sysMsg.put("content", systemPrompt);
        messages.add(sysMsg);

        // User message（多模态 content array）
        Map<String, Object> userMsg = new LinkedHashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userContentParts);
        messages.add(userMsg);

        body.put("messages", messages);

        String bodyJson;
        try {
            bodyJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(body);
        } catch (Exception e) {
            bodyJson = body.toString();
        }
        log.info("[OpenClaw] 多模态调用: model={}, parts={}", model, userContentParts.size());
        log.info("[OpenClaw] 多模态请求体: {}", bodyJson);

        return webClient.post()
                .uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .map(this::extractContent)
                .doOnSuccess(text -> log.info("[OpenClaw] 多模态成功: model={}, responseLen={}", model, text.length()))
                .doOnError(WebClientResponseException.class, err ->
                        log.error("[OpenClaw] 多模态HTTP错误: model={}, status={}, body={}",
                                model, err.getStatusCode(), err.getResponseBodyAsString()))
                .doOnError(err -> {
                    if (!(err instanceof WebClientResponseException)) {
                        log.error("[OpenClaw] 多模态调用失败: model={}, error={}", model, err.getMessage());
                    }
                });
    }

    /**
     * 从 OpenAI 兼容响应中提取 content 字段。
     * 响应格式：{"choices": [{"message": {"content": "..."}}]}
     */
    @SuppressWarnings("unchecked")
    private String extractContent(Map<?, ?> response) {
        // 检查是否有 error 字段（Gateway 错误）
        Object error = response.get("error");
        if (error != null) {
            throw new IllegalStateException("OpenClaw Gateway 返回错误: " + error);
        }

        Object choices = response.get("choices");
        if (!(choices instanceof List<?>) || ((List<?>) choices).isEmpty()) {
            throw new IllegalStateException("OpenClaw 响应缺少 choices: " + response);
        }
        Map<String, Object> first = (Map<String, Object>) ((List<?>) choices).get(0);
        Map<String, Object> message = (Map<String, Object>) first.get("message");
        if (message == null) {
            throw new IllegalStateException("OpenClaw 响应缺少 message: " + response);
        }
        Object content = message.get("content");
        if (content == null || content.toString().isBlank()) {
            log.warn("[OpenClaw] AI 返回空内容, response={}", response);
            throw new IllegalStateException("AI 返回内容为空");
        }
        return content.toString();
    }
}
