package com.lovetravel.server.modules.ai.vo;

import com.lovetravel.server.modules.plan.vo.PlanDayResponse;

public class AiPlanDayApplyResponse {

    private boolean success;
    private PlanDayResponse day;

    public AiPlanDayApplyResponse(boolean success, PlanDayResponse day) {
        this.success = success;
        this.day = day;
    }

    public boolean isSuccess() { return success; }
    public PlanDayResponse getDay() { return day; }
}
