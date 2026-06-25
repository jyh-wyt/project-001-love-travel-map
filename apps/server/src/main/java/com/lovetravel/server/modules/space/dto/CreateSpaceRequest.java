package com.lovetravel.server.modules.space.dto;

public class CreateSpaceRequest {

    private Long userId;

    private String spaceName;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getSpaceName() {
        return spaceName;
    }

    public void setSpaceName(String spaceName) {
        this.spaceName = spaceName;
    }
}
