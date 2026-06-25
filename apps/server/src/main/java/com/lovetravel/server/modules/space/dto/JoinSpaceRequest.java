package com.lovetravel.server.modules.space.dto;

import jakarta.validation.constraints.NotBlank;

public class JoinSpaceRequest {

    private Long userId;

    @NotBlank
    private String code;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
