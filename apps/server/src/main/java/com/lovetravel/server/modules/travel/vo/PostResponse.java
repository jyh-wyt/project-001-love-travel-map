package com.lovetravel.server.modules.travel.vo;

import java.time.LocalDateTime;
import java.util.List;

public class PostResponse {

    private Long id;
    private Long authorUserId;
    private String authorNickname;
    private String content;
    private String polishedContent;
    private String locationName;
    private LocalDateTime createdAt;
    private List<ImageResponse> images;

    public PostResponse(
            Long id,
            Long authorUserId,
            String authorNickname,
            String content,
            String polishedContent,
            String locationName,
            LocalDateTime createdAt,
            List<ImageResponse> images) {
        this.id = id;
        this.authorUserId = authorUserId;
        this.authorNickname = authorNickname;
        this.content = content;
        this.polishedContent = polishedContent;
        this.locationName = locationName;
        this.createdAt = createdAt;
        this.images = images;
    }

    public Long getId() {
        return id;
    }

    public Long getAuthorUserId() {
        return authorUserId;
    }

    public String getAuthorNickname() {
        return authorNickname;
    }

    public String getContent() {
        return content;
    }

    public String getPolishedContent() {
        return polishedContent;
    }

    public String getLocationName() {
        return locationName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public List<ImageResponse> getImages() {
        return images;
    }
}
