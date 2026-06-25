package com.lovetravel.server.modules.travel.vo;

import java.time.LocalDate;
import java.util.List;

public class TravelPageResponse {

    private Long tripId;
    private String provinceCode;
    private String provinceName;
    private String cityCode;
    private String cityName;
    private String title;
    private String coverImageUrl;
    private LocalDate startDate;
    private LocalDate endDate;
    private List<PostResponse> posts;

    public TravelPageResponse(
            Long tripId,
            String provinceCode,
            String provinceName,
            String cityCode,
            String cityName,
            String title,
            String coverImageUrl,
            LocalDate startDate,
            LocalDate endDate,
            List<PostResponse> posts) {
        this.tripId = tripId;
        this.provinceCode = provinceCode;
        this.provinceName = provinceName;
        this.cityCode = cityCode;
        this.cityName = cityName;
        this.title = title;
        this.coverImageUrl = coverImageUrl;
        this.startDate = startDate;
        this.endDate = endDate;
        this.posts = posts;
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

    public String getTitle() {
        return title;
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

    public List<PostResponse> getPosts() {
        return posts;
    }
}
