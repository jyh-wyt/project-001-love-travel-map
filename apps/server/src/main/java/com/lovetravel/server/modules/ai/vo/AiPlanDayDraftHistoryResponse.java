package com.lovetravel.server.modules.ai.vo;

import java.time.LocalDateTime;

public class AiPlanDayDraftHistoryResponse {

    private Long id;
    private String title;
    private String status;
    private String contentPreview;
    private LocalDateTime createdAt;
    private LocalDateTime appliedAt;

    public AiPlanDayDraftHistoryResponse(Long id, String title, String status, String contentPreview, LocalDateTime createdAt, LocalDateTime appliedAt) {
        this.id = id;
        this.title = title;
        this.status = status;
        this.contentPreview = contentPreview;
        this.createdAt = createdAt;
        this.appliedAt = appliedAt;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getStatus() { return status; }
    public String getContentPreview() { return contentPreview; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getAppliedAt() { return appliedAt; }
}
