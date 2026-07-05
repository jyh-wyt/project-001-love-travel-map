package com.lovetravel.server.modules.auth.service;

import com.lovetravel.server.common.UnauthorizedException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.Duration;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

@Service
public class AuthSessionService {

    public static final String COOKIE_NAME = "LOVE_TRAVEL_SESSION";
    private static final long MAX_AGE_SECONDS = 60L * 60L * 24L * 7L;
    private static final String SESSION_KEY_PREFIX = "love-travel:session:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final byte[] secret;
    private final StringRedisTemplate redisTemplate;

    @Autowired
    public AuthSessionService(
            StringRedisTemplate redisTemplate,
            @Value("${love-travel.auth.session-secret:dev-love-travel-session-secret-change-me}") String sessionSecret) {
        this.redisTemplate = redisTemplate;
        this.secret = sessionSecret.getBytes(StandardCharsets.UTF_8);
    }

    public AuthSessionService(@Value("${love-travel.auth.session-secret:dev-love-travel-session-secret-change-me}") String sessionSecret) {
        this.redisTemplate = null;
        this.secret = sessionSecret.getBytes(StandardCharsets.UTF_8);
    }

    public ResponseCookie createSessionCookie(Long userId) {
        long expiresAt = Instant.now().getEpochSecond() + MAX_AGE_SECONDS;
        String payload = userId + "." + expiresAt;
        String token = base64Url(payload) + "." + sign(payload);
        saveSession(token, userId);
        return ResponseCookie.from(COOKIE_NAME, token)
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/")
                .maxAge(MAX_AGE_SECONDS)
                .build();
    }

    public void deleteSession(HttpServletRequest request) {
        String token = findCookie(request);
        if (token != null && redisTemplate != null) {
            redisTemplate.delete(sessionKey(token));
        }
    }

    public ResponseCookie clearSessionCookie() {
        return ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();
    }

    public Long requireCurrentUserId(HttpServletRequest request) {
        String token = findCookie(request);
        if (token == null || token.isBlank()) {
            throw new UnauthorizedException("请先登录");
        }

        try {
            String[] parts = token.split("\\.", 2);
            if (parts.length != 2) {
                throw invalidSession();
            }

            String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            if (!MessageDigest.isEqual(sign(payload).getBytes(StandardCharsets.UTF_8), parts[1].getBytes(StandardCharsets.UTF_8))) {
                throw invalidSession();
            }

            String[] payloadParts = payload.split("\\.", 2);
            if (payloadParts.length != 2) {
                throw invalidSession();
            }

            long expiresAt = Long.parseLong(payloadParts[1]);
            if (expiresAt < Instant.now().getEpochSecond()) {
                throw new UnauthorizedException("登录已过期，请重新登录");
            }

            Long userId = Long.parseLong(payloadParts[0]);
            if (redisTemplate != null) {
                String redisUserId = redisTemplate.opsForValue().get(sessionKey(token));
                if (redisUserId == null) {
                    throw invalidSession();
                }
                if (!redisUserId.equals(String.valueOf(userId))) {
                    throw new UnauthorizedException("登录状态异常，请重新登录");
                }
                refreshSession(token, userId, expiresAt);
            }
            return userId;
        } catch (IllegalArgumentException exception) {
            throw invalidSession();
        }
    }

    private String findCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return base64Url(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign auth session", exception);
        }
    }

    private String base64Url(String value) {
        return base64Url(value.getBytes(StandardCharsets.UTF_8));
    }

    private String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private void saveSession(String token, Long userId) {
        if (redisTemplate == null) {
            return;
        }
        long ttlSeconds = MAX_AGE_SECONDS + RANDOM.nextInt(300);
        redisTemplate.opsForValue().set(sessionKey(token), String.valueOf(userId), Duration.ofSeconds(ttlSeconds));
    }

    private void refreshSession(String token, Long userId, long cookieExpiresAt) {
        long secondsLeft = cookieExpiresAt - Instant.now().getEpochSecond();
        if (secondsLeft <= 0) {
            redisTemplate.delete(sessionKey(token));
            return;
        }
        redisTemplate.expire(sessionKey(token), Duration.ofSeconds(secondsLeft + RANDOM.nextInt(300)));
    }

    private String sessionKey(String token) {
        return SESSION_KEY_PREFIX + sha256(token);
    }

    private UnauthorizedException invalidSession() {
        return new UnauthorizedException("登录状态已失效，请重新登录");
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return base64Url(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash auth session", exception);
        }
    }
}
