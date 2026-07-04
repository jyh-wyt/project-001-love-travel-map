package com.lovetravel.server.modules.plan.vo;

import java.util.List;

public class PlanDayResponse {

    private Long id;
    private String date;
    private String title;
    private String detail;
    private List<String> aiPlaces;
    private List<String> aiMustVisitPlaces;
    private String aiHotelLocation;
    private Integer sortOrder;

    public PlanDayResponse(Long id, String date, String title, String detail, List<String> aiPlaces, List<String> aiMustVisitPlaces, String aiHotelLocation, Integer sortOrder) {
        this.id = id;
        this.date = date;
        this.title = title;
        this.detail = detail;
        this.aiPlaces = aiPlaces;
        this.aiMustVisitPlaces = aiMustVisitPlaces;
        this.aiHotelLocation = aiHotelLocation;
        this.sortOrder = sortOrder;
    }

    public Long getId() {
        return id;
    }

    public String getDate() {
        return date;
    }

    public String getTitle() {
        return title;
    }

    public String getDetail() {
        return detail;
    }

    public List<String> getAiPlaces() {
        return aiPlaces;
    }

    public List<String> getAiMustVisitPlaces() {
        return aiMustVisitPlaces;
    }

    public String getAiHotelLocation() {
        return aiHotelLocation;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }
}
