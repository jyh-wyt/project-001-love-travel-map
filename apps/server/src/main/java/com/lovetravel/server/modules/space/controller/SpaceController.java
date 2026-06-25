package com.lovetravel.server.modules.space.controller;

import com.lovetravel.server.modules.auth.service.AuthSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.lovetravel.server.modules.space.vo.CoupleSpaceResponse;
import com.lovetravel.server.modules.space.dto.CreateInviteCodeRequest;
import com.lovetravel.server.modules.space.dto.CreateSpaceRequest;
import com.lovetravel.server.modules.space.vo.InviteCodeResponse;
import com.lovetravel.server.modules.space.dto.JoinSpaceRequest;
import com.lovetravel.server.modules.space.service.SpaceService;
import com.lovetravel.server.modules.space.dto.UnbindSpaceRequest;

@RestController
@RequestMapping("/api")
public class SpaceController {

    private final SpaceService spaceService;
    private final AuthSessionService authSessionService;

    public SpaceController(SpaceService spaceService, AuthSessionService authSessionService) {
        this.spaceService = spaceService;
        this.authSessionService = authSessionService;
    }

    @PostMapping("/spaces")
    public CoupleSpaceResponse createSpace(HttpServletRequest servletRequest, @Valid @RequestBody CreateSpaceRequest request) {
        Long userId = authSessionService.requireCurrentUserId(servletRequest);
        request.setUserId(userId);
        return spaceService.createSpace(request);
    }

    @GetMapping("/spaces")
    public List<CoupleSpaceResponse> listSpaces(HttpServletRequest servletRequest) {
        Long userId = authSessionService.requireCurrentUserId(servletRequest);
        return spaceService.listSpaces(userId);
    }

    @PostMapping("/spaces/couple")
    public CoupleSpaceResponse createCoupleSpace(HttpServletRequest servletRequest) {
        Long userId = authSessionService.requireCurrentUserId(servletRequest);
        return spaceService.createCoupleSpace(userId);
    }

    @PostMapping("/spaces/{spaceId}/activate")
    public CoupleSpaceResponse activateSpace(@PathVariable("spaceId") Long spaceId, HttpServletRequest servletRequest) {
        Long userId = authSessionService.requireCurrentUserId(servletRequest);
        return spaceService.activateSpace(userId, spaceId);
    }

    @GetMapping("/spaces/current")
    public CoupleSpaceResponse getCurrentSpace(HttpServletRequest servletRequest) {
        Long userId = authSessionService.requireCurrentUserId(servletRequest);
        return spaceService.getCurrentSpace(userId);
    }

    @PostMapping("/spaces/{spaceId}/invite-code")
    public InviteCodeResponse createInviteCode(
            @PathVariable("spaceId") Long spaceId,
            HttpServletRequest servletRequest,
            @Valid @RequestBody CreateInviteCodeRequest request) {
        request.setUserId(authSessionService.requireCurrentUserId(servletRequest));
        return spaceService.createInviteCode(spaceId, request);
    }

    @PostMapping("/invite-codes/join")
    public CoupleSpaceResponse joinByInviteCode(HttpServletRequest servletRequest, @Valid @RequestBody JoinSpaceRequest request) {
        request.setUserId(authSessionService.requireCurrentUserId(servletRequest));
        return spaceService.joinByInviteCode(request);
    }

    @PostMapping("/spaces/unbind")
    public CoupleSpaceResponse unbindSpace(HttpServletRequest servletRequest, @RequestBody UnbindSpaceRequest request) {
        request.setUserId(authSessionService.requireCurrentUserId(servletRequest));
        return spaceService.unbindSpace(request);
    }
}
