package com.lovetravel.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lovetravel.server.modules.ai.dto.AiPlanDayGenerateRequest;
import com.lovetravel.server.modules.ai.service.AiPlanQualityEvaluator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AiPlanQualityEvaluatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void evaluatesPlaceScopeMemoryAndRevisionWithoutStoringPromptText() throws Exception {
        AiPlanDayGenerateRequest request = new AiPlanDayGenerateRequest();
        request.setPlaces(List.of("八大关", "小麦岛"));
        request.setRegenerateMode("REVISE");
        request.setRevisionInstruction("少走路");

        JsonNode draft = objectMapper.readTree("""
                {
                  "morning": {"places": ["八大关"]},
                  "afternoon": {"places": ["小麦岛"]},
                  "evening": {"places": []}
                }
                """);

        Map<String, Object> quality = new AiPlanQualityEvaluator().evaluate(request, draft, 2, true);

        assertEquals("PASS", quality.get("placeScopeStatus"));
        assertEquals("USED", quality.get("memoryUsageStatus"));
        assertEquals("CHECKED", quality.get("revisionStatus"));
        assertFalse(quality.containsKey("prompt"));
        assertFalse(quality.containsKey("draft"));
    }
}
