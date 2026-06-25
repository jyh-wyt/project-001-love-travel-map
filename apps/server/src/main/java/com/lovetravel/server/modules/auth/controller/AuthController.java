package com.lovetravel.server.modules.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.lovetravel.server.modules.auth.vo.AuthResponse;
import com.lovetravel.server.modules.auth.service.AuthService;
import com.lovetravel.server.modules.auth.service.AuthSessionService;
import com.lovetravel.server.modules.auth.dto.LoginRequest;
import com.lovetravel.server.modules.auth.dto.RegisterRequest;
import com.lovetravel.server.modules.auth.dto.UpdateNicknameRequest;
import com.lovetravel.server.modules.auth.dto.UpdatePasswordRequest;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthSessionService authSessionService;

    public AuthController(AuthService authService, AuthSessionService authSessionService) {
        this.authService = authService;
        this.authSessionService = authSessionService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return withSessionCookie(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return withSessionCookie(response);
    }

    @GetMapping("/me")
    public AuthResponse me(HttpServletRequest request) {
        Long userId = authSessionService.requireCurrentUserId(request);
        return authService.getCurrentUser(userId);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Boolean>> logout(HttpServletRequest request) {
        authSessionService.deleteSession(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, authSessionService.clearSessionCookie().toString())
                .body(Map.of("success", true));
    }

    @PatchMapping("/me/nickname")
    public AuthResponse updateNickname(
            HttpServletRequest servletRequest,
            @RequestBody UpdateNicknameRequest request) {
        Long userId = authSessionService.requireCurrentUserId(servletRequest);
        return authService.updateNickname(userId, request);
    }

    @PatchMapping("/me/password")
    public AuthResponse updatePassword(
            HttpServletRequest servletRequest,
            @RequestBody UpdatePasswordRequest request) {
        Long userId = authSessionService.requireCurrentUserId(servletRequest);
        return authService.updatePassword(userId, request);
    }

    private ResponseEntity<AuthResponse> withSessionCookie(AuthResponse response) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, authSessionService.createSessionCookie(response.getUserId()).toString())
                .body(response);
    }
}
