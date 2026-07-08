package com.lovetravel.server.modules.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.lovetravel.server.modules.ai.dto.AiPlanDayGenerateRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AiPlanQualityEvaluator {

    public Map<String, Object> evaluate(
            AiPlanDayGenerateRequest request,
            JsonNode draft,
            int memoryCount,
            boolean memorySearchSuccess) {
        Set<String> allowedPlaces = normalizeSet(request.getPlaces());
        Set<String> outputPlaces = collectOutputPlaces(draft);
        Set<String> extraPlaces = new LinkedHashSet<>(outputPlaces);
        extraPlaces.removeAll(allowedPlaces);

        String placeScopeStatus = extraPlaces.isEmpty() ? "PASS" : "WARN";
        String memoryUsageStatus = memorySearchSuccess
                ? (memoryCount > 0 ? "USED" : "NO_MATCH")
                : "UNAVAILABLE";
        String revisionStatus = isReviseWithInstruction(request) ? "CHECKED" : "NOT_REQUIRED";

        List<String> summaries = new ArrayList<>();
        summaries.add(extraPlaces.isEmpty() ? "地点范围符合输入" : "发现未输入地点：" + String.join("、", extraPlaces));
        summaries.add(memoryCount > 0 ? "已参考历史记忆" : "未使用历史记忆");
        if (isReviseWithInstruction(request)) {
            summaries.add("已按修改要求重新生成");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("placeScopeStatus", placeScopeStatus);
        result.put("memoryUsageStatus", memoryUsageStatus);
        result.put("revisionStatus", revisionStatus);
        result.put("memoryCount", memoryCount);
        result.put("extraPlaces", List.copyOf(extraPlaces));
        result.put("summary", String.join("；", summaries));
        return result;
    }

    private boolean isReviseWithInstruction(AiPlanDayGenerateRequest request) {
        return "REVISE".equalsIgnoreCase(safeString(request.getRegenerateMode()))
                && !safeString(request.getRevisionInstruction()).isBlank();
    }

    private Set<String> collectOutputPlaces(JsonNode draft) {
        Set<String> places = new LinkedHashSet<>();
        collectPeriodPlaces(places, draft.path("morning"));
        collectPeriodPlaces(places, draft.path("afternoon"));
        collectPeriodPlaces(places, draft.path("evening"));
        return places;
    }

    private void collectPeriodPlaces(Set<String> places, JsonNode period) {
        JsonNode nodes = period.path("places");
        if (!nodes.isArray()) {
            return;
        }
        for (JsonNode node : nodes) {
            String place = normalize(node.asText(""));
            if (!place.isBlank()) {
                places.add(place);
            }
        }
    }

    private Set<String> normalizeSet(List<String> values) {
        Set<String> result = new LinkedHashSet<>();
        if (values == null) {
            return result;
        }
        for (String value : values) {
            String normalized = normalize(value);
            if (!normalized.isBlank()) {
                result.add(normalized);
            }
        }
        return result;
    }

    private String normalize(String value) {
        return safeString(value).replaceAll("\\s+", "");
    }

    private String safeString(String value) {
        return value == null ? "" : value.trim();
    }
}
