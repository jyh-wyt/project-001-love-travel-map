package com.lovetravel.server.modules.ai.vo;

import java.time.LocalDateTime;

public class AiAgentEventResponse {

    private Long id;
    private String eventType;
    private String eventMessage;
    private String eventJson;
    private LocalDateTime createdAt;

    public AiAgentEventResponse(Long id, String eventType, String eventMessage, String eventJson, LocalDateTime createdAt) {
        this.id = id;
        this.eventType = eventType;
        this.eventMessage = eventMessage;
        this.eventJson = eventJson;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getEventType() { return eventType; }
    public String getEventMessage() { return eventMessage; }
    public String getEventJson() { return eventJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
