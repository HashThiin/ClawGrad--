package com.openclaw.grading.service;

import com.openclaw.grading.model.entity.RefreshToken;
import com.openclaw.grading.model.entity.User;
import com.openclaw.grading.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();
    private final long expiresDays;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               @Value("${app.security.refresh-token.expires-days:14}") long expiresDays) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.expiresDays = expiresDays;
    }

    @Transactional
    public String issueToken(User user) {
        String rawToken = newToken();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setTokenHash(hash(rawToken));
        refreshToken.setUser(user);
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(expiresDays));
        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    @Transactional
    public User rotate(String rawToken) {
        RefreshToken refreshToken = requireValid(rawToken);
        refreshToken.setRevokedAt(LocalDateTime.now());
        User user = refreshToken.getUser();
        user.getUsername();
        user.getDisplayName();
        user.getRole();
        return user;
    }

    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(hash(rawToken)).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(LocalDateTime.now());
            }
        });
    }

    private RefreshToken requireValid(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "refresh token 无效");
        }
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "refresh token 无效"));
        if (refreshToken.getRevokedAt() != null || refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "refresh token 已失效");
        }
        return refreshToken;
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("refresh token hash 失败", e);
        }
    }
}
