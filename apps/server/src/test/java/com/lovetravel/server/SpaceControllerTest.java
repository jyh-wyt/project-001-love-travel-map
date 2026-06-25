package com.lovetravel.server;

import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lovetravel.server.modules.auth.service.AuthSessionService;
import com.lovetravel.server.common.GlobalExceptionHandler;
import com.lovetravel.server.modules.space.vo.CoupleSpaceResponse;
import com.lovetravel.server.modules.space.controller.SpaceController;
import com.lovetravel.server.modules.space.service.SpaceService;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SpaceControllerTest {

    private MockMvc mockMvc;
    private SpaceService spaceService;
    private AuthSessionService authSessionService;

    @BeforeEach
    void setUp() {
        spaceService = Mockito.mock(SpaceService.class);
        authSessionService = new AuthSessionService("test-session-secret-which-is-long-enough");
        mockMvc = MockMvcBuilders.standaloneSetup(new SpaceController(spaceService, authSessionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listSpacesReturnsCurrentPersonalSpace() throws Exception {
        Mockito.when(spaceService.listSpaces(7L))
                .thenReturn(List.of(new CoupleSpaceResponse(
                        11L,
                        "焦焦的旅行空间",
                        "PERSONAL",
                        "ACTIVE",
                        7L,
                        1,
                        1,
                        true,
                        true)));

        mockMvc.perform(get("/api/spaces").cookie(sessionCookie(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].spaceId").value(11))
                .andExpect(jsonPath("$[0].spaceName").value("焦焦的旅行空间"))
                .andExpect(jsonPath("$[0].spaceType").value("PERSONAL"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].current").value(true))
                .andExpect(jsonPath("$[0].editable").value(true));
    }

    @Test
    void createCoupleSpaceReturnsWaitingSpace() throws Exception {
        Mockito.when(spaceService.createCoupleSpace(7L))
                .thenReturn(new CoupleSpaceResponse(
                        12L,
                        "焦焦的情侣旅行空间",
                        "COUPLE",
                        "WAITING",
                        7L,
                        1,
                        2,
                        true,
                        false));

        mockMvc.perform(post("/api/spaces/couple")
                        .cookie(sessionCookie(7L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spaceType").value("COUPLE"))
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.editable").value(false));
    }

    @Test
    void activateSpaceRequiresMembershipAndReturnsCurrentSpace() throws Exception {
        Mockito.when(spaceService.activateSpace(eq(7L), eq(12L)))
                .thenReturn(new CoupleSpaceResponse(
                        12L,
                        "焦焦和然然的旅行空间",
                        "COUPLE",
                        "ACTIVE",
                        7L,
                        2,
                        2,
                        true,
                        true));

        mockMvc.perform(post("/api/spaces/12/activate")
                        .cookie(sessionCookie(7L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spaceId").value(12))
                .andExpect(jsonPath("$.current").value(true))
                .andExpect(jsonPath("$.editable").value(true));
    }

    private Cookie sessionCookie(Long userId) {
        return new Cookie(AuthSessionService.COOKIE_NAME, authSessionService.createSessionCookie(userId).getValue());
    }
}
