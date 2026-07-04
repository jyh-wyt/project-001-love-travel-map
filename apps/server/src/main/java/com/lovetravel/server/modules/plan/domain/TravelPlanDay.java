package com.lovetravel.server.modules.plan.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("travel_plan_day")
public class TravelPlanDay {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long spaceId;
    private LocalDate planDate;
    private String title;
    private String detail;
    private String aiPlacesJson;
    private String aiMustVisitPlacesJson;
    private String aiHotelLocation;
    private Integer sortOrder;
    private Long createdByUserId;
    private Long updatedByUserId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSpaceId() {
        return spaceId;
    }

    public void setSpaceId(Long spaceId) {
        this.spaceId = spaceId;
    }

    public LocalDate getPlanDate() {
        return planDate;
    }

    public void setPlanDate(LocalDate planDate) {
        this.planDate = planDate;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getAiPlacesJson() {
        return aiPlacesJson;
    }

    public void setAiPlacesJson(String aiPlacesJson) {
        this.aiPlacesJson = aiPlacesJson;
    }

    public String getAiMustVisitPlacesJson() {
        return aiMustVisitPlacesJson;
    }

    public void setAiMustVisitPlacesJson(String aiMustVisitPlacesJson) {
        this.aiMustVisitPlacesJson = aiMustVisitPlacesJson;
    }

    public String getAiHotelLocation() {
        return aiHotelLocation;
    }

    public void setAiHotelLocation(String aiHotelLocation) {
        this.aiHotelLocation = aiHotelLocation;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Long getCreatedByUserId() {
        return createdByUserId;
    }

    public void setCreatedByUserId(Long createdByUserId) {
        this.createdByUserId = createdByUserId;
    }

    public Long getUpdatedByUserId() {
        return updatedByUserId;
    }

    public void setUpdatedByUserId(Long updatedByUserId) {
        this.updatedByUserId = updatedByUserId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }
}
