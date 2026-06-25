package com.lovetravel.server;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lovetravel.server.modules.auth.service.AuthSessionService;
import com.lovetravel.server.modules.auth.controller.AuthController;
import com.lovetravel.server.modules.auth.vo.AuthResponse;
import com.lovetravel.server.modules.auth.service.AuthService;
import com.lovetravel.server.common.GlobalExceptionHandler;
import com.lovetravel.server.modules.plan.controller.PlanController;
import com.lovetravel.server.modules.plan.service.PlanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthSessionControllerTest {

    private MockMvc mockMvc;
    private AuthService authService;
    private PlanService planService;
    private AuthSessionService authSessionService;

    @BeforeEach
    void setUp() {
        authService = Mockito.mock(AuthService.class);
        planService = Mockito.mock(PlanService.class);
        authSessionService = new AuthSessionService("test-session-secret-which-is-long-enough");

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new AuthController(authService, authSessionService),
                        new PlanController(planService, authSessionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void protectedApiRejectsMissingSessionCookie() throws Exception {
        mockMvc.perform(get("/api/plans/days"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginWritesSessionCookie() throws Exception {
        Mockito.when(authService.login(Mockito.any()))
                .thenReturn(new AuthResponse(7L, "tester", "tester_name"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"tester\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("LOVE_TRAVEL_SESSION"));
    }
}
