package com.lovetravel.server.modules.oss.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "love-travel.oss")
public class OssProperties {

    private String bucket;
    private String region;
    private String secretId;
    private String secretKey;
    private String publicBaseUrl;
    private Integer signedUrlExpireMinutes = 10;

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getSecretId() {
        return secretId;
    }

    public void setSecretId(String secretId) {
        this.secretId = secretId;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public Integer getSignedUrlExpireMinutes() {
        return signedUrlExpireMinutes;
    }

    public void setSignedUrlExpireMinutes(Integer signedUrlExpireMinutes) {
        this.signedUrlExpireMinutes = signedUrlExpireMinutes;
    }
}
