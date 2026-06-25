package com.lovetravel.server.modules.ai.vo;

public class AiDiscardResponse {

    private boolean success;

    public AiDiscardResponse(boolean success) {
        this.success = success;
    }

    public boolean isSuccess() { return success; }
}
