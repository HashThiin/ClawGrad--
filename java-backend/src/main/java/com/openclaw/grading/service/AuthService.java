package com.openclaw.grading.service;

import com.openclaw.grading.model.dto.AuthRequest;
import com.openclaw.grading.model.dto.AuthResponse;
import com.openclaw.grading.model.dto.RegisterRequest;
import com.openclaw.grading.model.entity.User;
import com.openclaw.grading.repository.UserRepository;
import com.openclaw.grading.security.JwtService;
import com.openclaw.grading.security.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtService jwtService,
                       RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    public AuthResponse register(RegisterRequest request) {
        String username = normalizeUsername(request.getUsername());
        validatePassword(request.getPassword());
        if (userRepository.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名已存在");
        }

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setDisplayName(isBlank(request.getDisplayName()) ? username : request.getDisplayName().trim());
        user.setRole("STUDENT");
        user = userRepository.save(user);

        return toResponse(user);
    }

    public AuthResponse login(AuthRequest request) {
        String username = normalizeUsername(request.getUsername());
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, request.getPassword()));
        } catch (AuthenticationException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误"));
        return toResponse(user);
    }

    public AuthResponse refresh(String refreshToken) {
        User user = refreshTokenService.rotate(refreshToken);
        return toResponse(user);
    }

    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    private AuthResponse toResponse(User user) {
        UserPrincipal principal = UserPrincipal.from(user);
        String token = jwtService.generateToken(principal);
        String refreshToken = refreshTokenService.issueToken(user);
        return new AuthResponse(
                token,
                "Bearer",
                refreshToken,
                jwtService.getExpiresInSeconds(),
                principal.getId(),
                principal.getUsername(),
                principal.getDisplayName(),
                principal.getRole()
        );
    }

    private String normalizeUsername(String username) {
        if (isBlank(username)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名不能为空");
        }
        String value = username.trim();
        if (value.length() < 3 || value.length() > 64) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名长度需为 3-64 个字符");
        }
        return value;
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码至少 6 个字符");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
