package com.lovetravel.server.modules.plan.controller;

import com.lovetravel.server.modules.auth.service.AuthSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.lovetravel.server.modules.plan.vo.DeletePlanDayResponse;
import com.lovetravel.server.modules.plan.dto.PlanDayRequest;
import com.lovetravel.server.modules.plan.vo.PlanDayResponse;
import com.lovetravel.server.modules.plan.service.PlanService;

@RestController
@RequestMapping("/api/plans")
public class PlanController {

    private final PlanService planService;
    private final AuthSessionService authSessionService;

    public PlanController(PlanService planService, AuthSessionService authSessionService) {
        this.planService = planService;
        this.authSessionService = authSessionService;
    }

    @GetMapping("/days")
    public List<PlanDayResponse> listDays(HttpServletRequest servletRequest) {
        Long userId = authSessionService.requireCurrentUserId(servletRequest);
        return planService.listDays(userId);
    }

    @PostMapping("/days")
    public PlanDayResponse createDay(HttpServletRequest servletRequest, @Valid @RequestBody PlanDayRequest request) {
        request.setUserId(authSessionService.requireCurrentUserId(servletRequest));
        return planService.createDay(request);
    }

    @PutMapping("/days/{dayId}")
    public PlanDayResponse updateDay(
            @PathVariable("dayId") Long dayId,
            HttpServletRequest servletRequest,
            @Valid @RequestBody PlanDayRequest request) {
        request.setUserId(authSessionService.requireCurrentUserId(servletRequest));
        return planService.updateDay(dayId, request);
    }

    @DeleteMapping("/days/{dayId}")
    public DeletePlanDayResponse deleteDay(@PathVariable("dayId") Long dayId, HttpServletRequest servletRequest) {
        Long userId = authSessionService.requireCurrentUserId(servletRequest);
        planService.deleteDay(dayId, userId);
        return new DeletePlanDayResponse(true);
    }
}
