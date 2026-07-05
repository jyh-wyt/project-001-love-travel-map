package com.lovetravel.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.lovetravel.server.common.UnauthorizedException;
import com.lovetravel.server.modules.auth.service.AuthSessionService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class AuthSessionServiceTest {

    private final AuthSessionService authSessionService = new AuthSessionService("test-session-secret-which-is-long-enough");

    @Test
    void malformedSessionCookieIsRejectedAsUnauthorized() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AuthSessionService.COOKIE_NAME, "not-a-valid-session-token"));

        UnauthorizedException exception = assertThrows(UnauthorizedException.class, () -> authSessionService.requireCurrentUserId(request));

        assertEquals("登录状态已失效，请重新登录", exception.getMessage());
    }
}
