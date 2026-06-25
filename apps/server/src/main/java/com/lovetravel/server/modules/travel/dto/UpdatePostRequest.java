package com.lovetravel.server.modules.travel.dto;

import jakarta.validation.Valid;
import java.util.List;

public class UpdatePostRequest {

    private Long userId;

    private String content;
    private String locationName;

    @Valid
    private List<ImageRequest> images;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getLocationName() {
        return locationName;
    }

    public void setLocationName(String locationName) {
        this.locationName = locationName;
    }

    public List<ImageRequest> getImages() {
        return images;
    }

    public void setImages(List<ImageRequest> images) {
        this.images = images;
    }
}
