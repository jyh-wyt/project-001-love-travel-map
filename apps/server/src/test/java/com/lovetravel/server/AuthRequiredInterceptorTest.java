package com.lovetravel.server;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.lovetravel.server.common.UnauthorizedException;
import com.lovetravel.server.config.AuthRequiredInterceptor;
import com.lovetravel.server.modules.auth.service.AuthSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthRequiredInterceptorTest {

    private final AuthSessionService authSessionService = new AuthSessionService("test-session-secret-which-is-long-enough");
    private final AuthRequiredInterceptor interceptor = new AuthRequiredInterceptor(authSessionService);

    @Test
    void publicAuthApisPassWithoutSession() {
        assertTrue(preHandle("POST", "/api/auth/register"));
        assertTrue(preHandle("POST", "/api/auth/login"));
        assertTrue(preHandle("POST", "/api/auth/logout"));
    }

    @Test
    void corsPreflightPassesWithoutSession() {
        assertTrue(preHandle("OPTIONS", "/api/plans/days"));
    }

    @Test
    void privateApiRejectsMissingSession() {
        assertThrows(UnauthorizedException.class, () -> preHandle("GET", "/api/plans/days"));
        assertThrows(UnauthorizedException.class, () -> preHandle("GET", "/api/oss/status"));
    }

    private boolean preHandle(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        MockHttpServletResponse response = new MockHttpServletResponse();
        return interceptor.preHandle(request, response, new Object());
    }
}
