package com.lovetravel.server.modules.travel.dto;

import jakarta.validation.constraints.NotNull;

public class SetCoverImageRequest {

    private Long userId;

    @NotNull
    private Long imageId;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getImageId() {
        return imageId;
    }

    public void setImageId(Long imageId) {
        this.imageId = imageId;
    }
}
