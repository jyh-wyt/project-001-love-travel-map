package com.lovetravel.server.config;

import com.lovetravel.server.modules.auth.service.AuthSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthRequiredInterceptor implements HandlerInterceptor {

    private final AuthSessionService authSessionService;

    public AuthRequiredInterceptor(AuthSessionService authSessionService) {
        this.authSessionService = authSessionService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || isPublicApi(request.getRequestURI())) {
            return true;
        }
        authSessionService.requireCurrentUserId(request);
        return true;
    }

    private boolean isPublicApi(String uri) {
        return "/api/auth/register".equals(uri)
                || "/api/auth/login".equals(uri)
                || "/api/auth/logout".equals(uri);
    }
}
