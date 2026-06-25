package com.lovetravel.server.modules.travel.dto;

import jakarta.validation.constraints.NotBlank;

public class ImageRequest {

    @NotBlank
    private String ossObjectKey;

    @NotBlank
    private String imageUrl;

    private Integer sortOrder;

    public String getOssObjectKey() {
        return ossObjectKey;
    }

    public void setOssObjectKey(String ossObjectKey) {
        this.ossObjectKey = ossObjectKey;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
