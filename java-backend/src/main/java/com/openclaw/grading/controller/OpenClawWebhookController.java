package com.openclaw.grading.controller;

import com.openclaw.grading.service.OpenClawClientService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * OpenClaw Webhook 控制器
 * 接收 OpenClaw Gateway 转发的消息
 */
@Slf4j
@RestController
@RequestMapping("/webhook")
public class OpenClawWebhookController {
    
    private final OpenClawClientService openClawClientService;
    
    public OpenClawWebhookController(OpenClawClientService openClawClientService) {
        this.openClawClientService = openClawClientService;
    }
    
    /**
     * 接收 OpenClaw Gateway 的 webhook 消息
     * 
     * POST /api/v1/webhook/openclaw
     */
    @PostMapping("/openclaw")
    public ResponseEntity<Map<String, Object>> receiveWebhook(@RequestBody OpenClawWebhookPayload payload) {
        log.info("Received OpenClaw webhook: channel={}, sender={}, type={}",
                payload.getSender().getChannel(),
                payload.getSender().getId(),
                payload.getMessage().getType());
        
        // TODO: 将消息处理任务放入异步队列
        // 立即返回响应，告诉 OpenClaw 已接收
        return ResponseEntity.ok(Map.of(
                "status", "received",
                "message", "消息已接收，正在处理中...",
                "session", payload.getSession().getContext()
        ));
    }
    
    /**
     * Webhook 健康检查
     * 
     * GET /api/v1/webhook/openclaw/health
     */
    @GetMapping("/openclaw/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "webhook", "active"
        ));
    }
    
    /**
     * OpenClaw Webhook 消息体
     */
    @Data
    public static class OpenClawWebhookPayload {
        private Sender sender;
        private Message message;
        private Session session;
        
        @Data
        public static class Sender {
            private String id;
            private String name;
            private String channel;
        }
        
        @Data
        public static class Message {
            private String type;  // text / file / image
            private String content;
            private String timestamp;
            private List<Attachment> attachments;
        }
        
        @Data
        public static class Attachment {
            private String downloadableUrl;
            private String fileName;
            private String mimeType;
        }
        
        @Data
        public static class Session {
            private String id;
            private Map<String, Object> context;
        }
    }
}
