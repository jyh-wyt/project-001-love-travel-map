package com.lovetravel.server.modules.plan.vo;

public class DeletePlanDayResponse {

    private boolean success;

    public DeletePlanDayResponse(boolean success) {
        this.success = success;
    }

    public boolean isSuccess() {
        return success;
    }
}
