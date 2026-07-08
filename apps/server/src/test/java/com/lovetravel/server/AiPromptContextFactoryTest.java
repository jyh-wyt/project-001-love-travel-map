package com.lovetravel.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lovetravel.server.modules.ai.dto.AiPlanDayGenerateRequest;
import com.lovetravel.server.modules.ai.service.AiPromptContextFactory;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AiPromptContextFactoryTest {

    @Test
    void buildsSafePromptMetadataWithoutPromptText() {
        AiPlanDayGenerateRequest request = new AiPlanDayGenerateRequest();
        request.setDestination("青岛");
        request.setPlaces(List.of("八大关", "小麦岛"));
        request.setHotelLocation("五四广场附近酒店");
        request.setNotes("想看海边日落");
        request.setRevisionInstruction("少走路");
        request.setRegenerateMode("REVISE");

        Map<String, Object> context = new AiPromptContextFactory()
                .buildPlanDayContext("travel_day_plan_v1", "qwen-plus", request, 3, true);

        assertEquals("travel_day_plan_v1", context.get("promptVersion"));
        assertEquals("qwen-plus", context.get("modelName"));
        assertEquals("REVISE", context.get("regenerateMode"));
        assertEquals(2, context.get("placeCount"));
        assertEquals(3, context.get("memoryCount"));
        assertEquals(true, context.get("hasHotelLocation"));
        assertEquals(true, context.get("hasRevisionInstruction"));
        assertTrue(((List<?>) context.get("enabledModules")).contains("memory_retrieval"));
        assertFalse(context.containsKey("prompt"));
        assertFalse(context.containsKey("notes"));
    }
}
