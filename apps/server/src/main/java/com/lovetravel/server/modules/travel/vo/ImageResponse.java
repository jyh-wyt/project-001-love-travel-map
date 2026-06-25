package com.lovetravel.server.modules.travel.vo;

public class ImageResponse {

    private Long id;
    private String imageUrl;
    private String ossObjectKey;
    private Integer sortOrder;

    public ImageResponse(Long id, String imageUrl, String ossObjectKey, Integer sortOrder) {
        this.id = id;
        this.imageUrl = imageUrl;
        this.ossObjectKey = ossObjectKey;
        this.sortOrder = sortOrder;
    }

    public Long getId() {
        return id;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getOssObjectKey() {
        return ossObjectKey;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }
}
