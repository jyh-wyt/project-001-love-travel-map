package com.lovetravel.server;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lovetravel.server.modules.ai.dto.AiPlanDayGenerateRequest;
import com.lovetravel.server.modules.ai.service.AiTravelMemoryReasoner;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AiTravelMemoryReasonerTest {

    @Test
    void explainsCityPlaceKeywordAndScoreMatches() {
        AiPlanDayGenerateRequest request = new AiPlanDayGenerateRequest();
        request.setDestination("青岛");
        request.setPlaces(List.of("小麦岛", "八大关"));
        request.setMustVisitPlaces(List.of("小麦岛"));
        request.setNotes("想看海边日落");

        Map<String, Object> memory = Map.of(
                "cityName", "青岛市",
                "content", "上次在小麦岛海边看日落，拍照很好看",
                "score", 0.18);

        String reason = new AiTravelMemoryReasoner().explain(request, memory);

        assertTrue(reason.contains("城市匹配"));
        assertTrue(reason.contains("小麦岛"));
        assertTrue(reason.contains("海边"));
        assertTrue(reason.contains("高相似度"));
    }
}
