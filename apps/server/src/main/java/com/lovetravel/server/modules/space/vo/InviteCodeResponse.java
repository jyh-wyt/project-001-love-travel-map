package com.lovetravel.server.modules.space.vo;

import java.time.LocalDateTime;

public class InviteCodeResponse {

    private String code;
    private LocalDateTime expireAt;

    public InviteCodeResponse(String code, LocalDateTime expireAt) {
        this.code = code;
        this.expireAt = expireAt;
    }

    public String getCode() {
        return code;
    }

    public LocalDateTime getExpireAt() {
        return expireAt;
    }
}

