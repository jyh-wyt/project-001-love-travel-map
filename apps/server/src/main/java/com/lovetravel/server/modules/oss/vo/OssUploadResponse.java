package com.lovetravel.server.modules.oss.vo;

public class OssUploadResponse {

    private String imageUrl;
    private String ossObjectKey;

    public OssUploadResponse(String imageUrl, String ossObjectKey) {
        this.imageUrl = imageUrl;
        this.ossObjectKey = ossObjectKey;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getOssObjectKey() {
        return ossObjectKey;
    }
}
