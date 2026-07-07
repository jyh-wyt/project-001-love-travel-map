package com.lovetravel.server.modules.ai.service;

import com.lovetravel.server.modules.ai.dto.AiPlanDayGenerateRequest;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AiTravelMemoryReasoner {

    public String explain(AiPlanDayGenerateRequest request, Map<String, Object> memory) {
        List<String> reasons = new ArrayList<>();
        String cityName = stringValue(memory.get("cityName"));
        String content = stringValue(memory.get("content"));
        double score = numberValue(memory.get("score"));

        String destination = stringValue(request.getDestination());
        if (!destination.isBlank()
                && (containsNormalized(cityName, destination) || containsNormalized(content, destination))) {
            reasons.add("城市匹配：" + destination);
        }

        Set<String> placeMatches = new LinkedHashSet<>();
        addMatches(placeMatches, content, request.getPlaces());
        addMatches(placeMatches, content, request.getMustVisitPlaces());
        if (!placeMatches.isEmpty()) {
            reasons.add("地点匹配：" + String.join("、", placeMatches));
        }

        Set<String> keywordMatches = new LinkedHashSet<>();
        addMatches(keywordMatches, content, extractKeywords(request.getNotes()));
        if (!keywordMatches.isEmpty()) {
            reasons.add("偏好匹配：" + String.join("、", keywordMatches));
        }

        reasons.add(formatSimilarity(score));
        return String.join("；", reasons);
    }

    private void addMatches(Set<String> matches, String content, List<String> candidates) {
        if (candidates == null || content.isBlank()) {
            return;
        }
        for (String candidate : candidates) {
            String value = stringValue(candidate);
            if (value.length() >= 2 && containsNormalized(content, value)) {
                matches.add(value);
            }
        }
    }

    private List<String> extractKeywords(String notes) {
        String normalized = normalize(notes);
        if (normalized.isBlank()) {
            return List.of();
        }

        List<String> keywords = new ArrayList<>();
        for (String token : normalized.split("[,，。.!！?？、；;\\s]+")) {
            if (token.length() >= 2) {
                keywords.add(token);
            }
        }

        List<String> commonTravelSignals = List.of("海边", "日落", "拍照", "散步", "美食", "夜景", "亲子", "安静", "休闲");
        for (String signal : commonTravelSignals) {
            if (normalized.contains(signal)) {
                keywords.add(signal);
            }
        }
        return keywords;
    }

    private String formatSimilarity(double score) {
        if (score <= 0.25) {
            return "高相似度";
        }
        if (score <= 0.55) {
            return "中等相似度";
        }
        return "低相似度";
    }

    private boolean containsNormalized(String text, String keyword) {
        String normalizedText = normalize(text);
        String normalizedKeyword = normalize(keyword);
        return !normalizedKeyword.isBlank() && normalizedText.contains(normalizedKeyword);
    }

    private String normalize(String value) {
        return stringValue(value).replaceAll("\\s+", "").toLowerCase();
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private double numberValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(stringValue(value));
        } catch (NumberFormatException exception) {
            return 1.0;
        }
    }
}
