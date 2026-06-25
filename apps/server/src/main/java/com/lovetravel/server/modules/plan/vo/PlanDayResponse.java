package com.lovetravel.server.modules.plan.vo;

public class PlanDayResponse {

    private Long id;
    private String date;
    private String title;
    private String detail;
    private Integer sortOrder;

    public PlanDayResponse(Long id, String date, String title, String detail, Integer sortOrder) {
        this.id = id;
        this.date = date;
        this.title = title;
        this.detail = detail;
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

    public Integer getSortOrder() {
        return sortOrder;
    }
}
