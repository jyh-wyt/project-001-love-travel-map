package com.lovetravel.server.modules.ai.vo;

import java.time.LocalDateTime;
import java.util.List;

public class AiAgentRunEventsResponse {

    private String runId;
    private String agentType;
    private String modelName;
    private String promptVersion;
    private String status;
    private Integer durationMs;
    private LocalDateTime createdAt;
    private List<AiAgentEventResponse> events;

    public AiAgentRunEventsResponse(
            String runId,
            String agentType,
            String modelName,
            String promptVersion,
            String status,
            Integer durationMs,
            LocalDateTime createdAt,
            List<AiAgentEventResponse> events) {
        this.runId = runId;
        this.agentType = agentType;
        this.modelName = modelName;
        this.promptVersion = promptVersion;
        this.status = status;
        this.durationMs = durationMs;
        this.createdAt = createdAt;
        this.events = events;
    }

    public String getRunId() { return runId; }
    public String getAgentType() { return agentType; }
    public String getModelName() { return modelName; }
    public String getPromptVersion() { return promptVersion; }
    public String getStatus() { return status; }
    public Integer getDurationMs() { return durationMs; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public List<AiAgentEventResponse> getEvents() { return events; }
}
