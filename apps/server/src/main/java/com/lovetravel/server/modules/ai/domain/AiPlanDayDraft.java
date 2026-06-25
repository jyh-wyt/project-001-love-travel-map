package com.lovetravel.server.modules.ai.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("ai_plan_day_draft")
public class AiPlanDayDraft {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String runId;
    private Long spaceId;
    private Long userId;
    private Long planDayId;
    private String title;
    private String morningJson;
    private String afternoonJson;
    private String eveningJson;
    private String recommendationsJson;
    private String tipsJson;
    private String status;
    private LocalDateTime appliedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public Long getSpaceId() { return spaceId; }
    public void setSpaceId(Long spaceId) { this.spaceId = spaceId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getPlanDayId() { return planDayId; }
    public void setPlanDayId(Long planDayId) { this.planDayId = planDayId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getMorningJson() { return morningJson; }
    public void setMorningJson(String morningJson) { this.morningJson = morningJson; }
    public String getAfternoonJson() { return afternoonJson; }
    public void setAfternoonJson(String afternoonJson) { this.afternoonJson = afternoonJson; }
    public String getEveningJson() { return eveningJson; }
    public void setEveningJson(String eveningJson) { this.eveningJson = eveningJson; }
    public String getRecommendationsJson() { return recommendationsJson; }
    public void setRecommendationsJson(String recommendationsJson) { this.recommendationsJson = recommendationsJson; }
    public String getTipsJson() { return tipsJson; }
    public void setTipsJson(String tipsJson) { this.tipsJson = tipsJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getAppliedAt() { return appliedAt; }
    public void setAppliedAt(LocalDateTime appliedAt) { this.appliedAt = appliedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
