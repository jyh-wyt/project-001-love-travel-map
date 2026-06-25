package com.lovetravel.server.modules.travel.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class VisitedRegionResponse {

    private Long tripId;
    private String provinceCode;
    private String provinceName;
    private String cityCode;
    private String cityName;
    private String coverImageUrl;
    private LocalDate startDate;
    private LocalDate endDate;
    private Long recordCount;
    private LocalDateTime updatedAt;

    public VisitedRegionResponse(
            Long tripId,
            String provinceCode,
            String provinceName,
            String cityCode,
            String cityName,
            String coverImageUrl,
            LocalDate startDate,
            LocalDate endDate,
            Long recordCount,
            LocalDateTime updatedAt) {
        this.tripId = tripId;
        this.provinceCode = provinceCode;
        this.provinceName = provinceName;
        this.cityCode = cityCode;
        this.cityName = cityName;
        this.coverImageUrl = coverImageUrl;
        this.startDate = startDate;
        this.endDate = endDate;
        this.recordCount = recordCount;
        this.updatedAt = updatedAt;
    }

    public Long getTripId() {
        return tripId;
    }

    public String getProvinceCode() {
        return provinceCode;
    }

    public String getProvinceName() {
        return provinceName;
    }

    public String getCityCode() {
        return cityCode;
    }

    public String getCityName() {
        return cityName;
    }

    public String getCoverImageUrl() {
        return coverImageUrl;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public Long getRecordCount() {
        return recordCount;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
