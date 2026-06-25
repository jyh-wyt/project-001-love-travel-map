package com.lovetravel.server.modules.ai.controller;

import com.lovetravel.server.modules.auth.service.AuthSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.lovetravel.server.modules.ai.vo.AiDiscardResponse;
import com.lovetravel.server.modules.ai.vo.AiPlanDayApplyResponse;
import com.lovetravel.server.modules.ai.dto.AiPlanDayGenerateRequest;
import com.lovetravel.server.modules.ai.service.AiPlanDayService;

@RestController
@RequestMapping("/api/ai")
public class AiPlanDayController {

    private final AiPlanDayService aiPlanDayService;
    private final AuthSessionService authSessionService;

    public AiPlanDayController(AiPlanDayService aiPlanDayService, AuthSessionService authSessionService) {
        this.aiPlanDayService = aiPlanDayService;
        this.authSessionService = authSessionService;
    }

    @PostMapping(value = "/plan-days/{dayId}/generate-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generatePlanDay(
            @PathVariable("dayId") Long dayId,
            HttpServletRequest servletRequest,
            @Valid @RequestBody AiPlanDayGenerateRequest request) {
        Long userId = authSessionService.requireCurrentUserId(servletRequest);
        return aiPlanDayService.generatePlanDay(userId, dayId, request);
    }

    @PostMapping("/plan-day-drafts/{draftId}/apply")
    public AiPlanDayApplyResponse applyDraft(@PathVariable("draftId") Long draftId, HttpServletRequest servletRequest) {
        Long userId = authSessionService.requireCurrentUserId(servletRequest);
        return aiPlanDayService.applyDraft(userId, draftId);
    }

    @PostMapping("/plan-day-drafts/{draftId}/discard")
    public AiDiscardResponse discardDraft(@PathVariable("draftId") Long draftId, HttpServletRequest servletRequest) {
        Long userId = authSessionService.requireCurrentUserId(servletRequest);
        aiPlanDayService.discardDraft(userId, draftId);
        return new AiDiscardResponse(true);
    }
}
