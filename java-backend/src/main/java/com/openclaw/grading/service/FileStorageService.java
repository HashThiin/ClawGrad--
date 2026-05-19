package com.openclaw.grading.service;

import com.openclaw.grading.pipeline.GradingContext.ImageAttachment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;

/**
 * 文件存储服务：负责将上传的图片落盘到 storage/{taskId}/，
 * 并把图片转成 Base64 供后续流水线使用。
 */
@Slf4j
@Service
public class FileStorageService {

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/png", "image/jpeg", "image/jpg", "image/webp", "image/gif", "image/bmp"
    );

    @Value("${grading.storage.dir:./storage}")
    private String storageDir;

    /**
     * 保存上传图片，返回包含 base64 的 ImageAttachment 列表。
     *
     * @param taskId 任务ID（作为子目录）
     * @param files 前端上传的文件
     */
    public List<ImageAttachment> saveImages(String taskId, MultipartFile[] files) throws IOException {
        List<ImageAttachment> result = new ArrayList<>();
        if (files == null || files.length == 0) {
            return result;
        }
        Path taskDir = Paths.get(storageDir, taskId);
        Files.createDirectories(taskDir);

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String contentType = file.getContentType();
            if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase())) {
                throw new IllegalArgumentException("不支持的文件类型: " + contentType +
                        "（仅允许 " + ALLOWED_IMAGE_TYPES + "）");
            }
            String original = file.getOriginalFilename();
            String safe = sanitize(original);
            Path target = taskDir.resolve(safe);
            byte[] bytes = file.getBytes();
            Files.write(target, bytes);
            String base64 = Base64.getEncoder().encodeToString(bytes);
            result.add(new ImageAttachment(safe, contentType, base64));
            log.info("Saved upload: taskId={}, file={}, size={}KB", taskId, safe, bytes.length / 1024);
        }
        return result;
    }

    private String sanitize(String name) {
        if (name == null || name.isBlank()) {
            return "image-" + System.currentTimeMillis();
        }
        // 仅保留基本字符，避免路径穿越
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
