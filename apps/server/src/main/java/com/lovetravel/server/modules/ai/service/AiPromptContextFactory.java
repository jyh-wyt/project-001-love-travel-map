package com.lovetravel.server.modules.ai.service;

import com.lovetravel.server.modules.ai.dto.AiPlanDayGenerateRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AiPromptContextFactory {

    public Map<String, Object> buildPlanDayContext(
            String promptVersion,
            String modelName,
            AiPlanDayGenerateRequest request,
            int memoryCount,
            boolean memorySearchSuccess) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("promptVersion", promptVersion);
        context.put("modelName", modelName);
        context.put("agentType", "TRAVEL_DAY_PLAN");
        context.put("enabledModules", List.of(
                "structured_json_output",
                "place_constraint",
                "weather_context",
                "memory_retrieval",
                "revision_instruction"));
        context.put("destination", safeString(request.getDestination()));
        context.put("placeCount", request.getPlaces() == null ? 0 : request.getPlaces().size());
        context.put("mustVisitCount", request.getMustVisitPlaces() == null ? 0 : request.getMustVisitPlaces().size());
        context.put("hasHotelLocation", !safeString(request.getHotelLocation()).isBlank());
        context.put("hasNotes", !safeString(request.getNotes()).isBlank());
        context.put("hasRevisionInstruction", !safeString(request.getRevisionInstruction()).isBlank());
        context.put("regenerateMode", normalizeRegenerateMode(request.getRegenerateMode()));
        context.put("memorySearchSuccess", memorySearchSuccess);
        context.put("memoryCount", memoryCount);
        return context;
    }

    private String normalizeRegenerateMode(String value) {
        return "REVISE".equalsIgnoreCase(safeString(value)) ? "REVISE" : "NEW";
    }

    private String safeString(String value) {
        return value == null ? "" : value.trim();
    }
}
