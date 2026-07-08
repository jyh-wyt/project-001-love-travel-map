package com.lovetravel.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;

import com.lovetravel.server.modules.ai.domain.AiAgentEvent;
import com.lovetravel.server.modules.ai.domain.AiAgentRun;
import com.lovetravel.server.modules.ai.mapper.AiAgentEventMapper;
import com.lovetravel.server.modules.ai.mapper.AiAgentRunMapper;
import com.lovetravel.server.modules.ai.mapper.AiPlanDayDraftMapper;
import com.lovetravel.server.modules.ai.service.AiPlanDayService;
import com.lovetravel.server.modules.ai.service.AiTravelMemorySyncService;
import com.lovetravel.server.modules.ai.vo.AiAgentRunEventsResponse;
import com.lovetravel.server.modules.auth.mapper.AppUserMapper;
import com.lovetravel.server.modules.plan.mapper.TravelPlanDayMapper;
import com.lovetravel.server.modules.space.domain.CoupleSpace;
import com.lovetravel.server.modules.space.service.SpaceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;

class AiAgentRunEventsTest {

    @Test
    void listRunEventsReturnsRunAndEventsForCurrentSpace() {
        SpaceService spaceService = Mockito.mock(SpaceService.class);
        AiAgentRunMapper runMapper = Mockito.mock(AiAgentRunMapper.class);
        AiAgentEventMapper eventMapper = Mockito.mock(AiAgentEventMapper.class);

        CoupleSpace space = new CoupleSpace();
        space.setId(11L);
        Mockito.when(spaceService.requireActiveSpace(7L)).thenReturn(space);

        AiAgentRun run = new AiAgentRun();
        run.setRunId("ai_run_1");
        run.setSpaceId(11L);
        run.setAgentType("TRAVEL_DAY_PLAN");
        run.setModelName("qwen-plus");
        run.setPromptVersion("travel_day_plan_v1");
        run.setStatus("SUCCESS");
        run.setDeleted(0);
        Mockito.when(runMapper.selectOne(any())).thenReturn(run);

        AiAgentEvent toolEvent = new AiAgentEvent();
        toolEvent.setId(1L);
        toolEvent.setRunId("ai_run_1");
        toolEvent.setEventType("TOOL_RESULT");
        toolEvent.setEventMessage("地点约束工具完成");
        toolEvent.setEventJson("{\"toolName\":\"place_constraint\"}");
        Mockito.when(eventMapper.selectList(any())).thenReturn(List.of(toolEvent));

        AiPlanDayService service = new AiPlanDayService(
                spaceService,
                Mockito.mock(TravelPlanDayMapper.class),
                runMapper,
                eventMapper,
                Mockito.mock(AiPlanDayDraftMapper.class),
                Mockito.mock(AppUserMapper.class),
                Mockito.mock(AiTravelMemorySyncService.class),
                new ObjectMapper(),
                Mockito.mock(StringRedisTemplate.class),
                "http://127.0.0.1:8000");

        AiAgentRunEventsResponse response = service.listRunEvents(7L, "ai_run_1");

        assertEquals("ai_run_1", response.getRunId());
        assertEquals("SUCCESS", response.getStatus());
        assertEquals(1, response.getEvents().size());
        assertEquals("TOOL_RESULT", response.getEvents().get(0).getEventType());
    }

    @Test
    void listRunEventsKeepsFullToolResultJson() {
        SpaceService spaceService = Mockito.mock(SpaceService.class);
        AiAgentRunMapper runMapper = Mockito.mock(AiAgentRunMapper.class);
        AiAgentEventMapper eventMapper = Mockito.mock(AiAgentEventMapper.class);

        CoupleSpace space = new CoupleSpace();
        space.setId(11L);
        Mockito.when(spaceService.requireActiveSpace(7L)).thenReturn(space);

        AiAgentRun run = new AiAgentRun();
        run.setRunId("ai_run_1");
        run.setSpaceId(11L);
        run.setAgentType("TRAVEL_DAY_PLAN");
        run.setModelName("qwen-plus");
        run.setPromptVersion("travel_day_plan_v1");
        run.setStatus("SUCCESS");
        run.setDeleted(0);
        Mockito.when(runMapper.selectOne(any())).thenReturn(run);

        String toolJson = "{\"toolName\":\"memory_retrieval\",\"label\":\"历史记忆检索工具\",\"summary\":\"已提供 2 条 RAG Top 记忆作为偏好参考\",\"data\":{\"topMemories\":[{\"sourceType\":\"TRIP_POST\",\"cityName\":\"青岛\",\"content\":\"去海边看日落\",\"score\":0.12}]}}";
        AiAgentEvent toolEvent = new AiAgentEvent();
        toolEvent.setId(1L);
        toolEvent.setRunId("ai_run_1");
        toolEvent.setEventType("TOOL_RESULT");
        toolEvent.setEventMessage("已提供 2 条 RAG Top 记忆作为偏好参考");
        toolEvent.setEventJson(toolJson);
        Mockito.when(eventMapper.selectList(any())).thenReturn(List.of(toolEvent));

        AiPlanDayService service = new AiPlanDayService(
                spaceService,
                Mockito.mock(TravelPlanDayMapper.class),
                runMapper,
                eventMapper,
                Mockito.mock(AiPlanDayDraftMapper.class),
                Mockito.mock(AppUserMapper.class),
                Mockito.mock(AiTravelMemorySyncService.class),
                new ObjectMapper(),
                Mockito.mock(StringRedisTemplate.class),
                "http://127.0.0.1:8000");

        AiAgentRunEventsResponse response = service.listRunEvents(7L, "ai_run_1");

        assertEquals(toolJson, response.getEvents().get(0).getEventJson());
    }

    @Test
    @SuppressWarnings("unchecked")
    void memoryToolResultIncludesRetrievalQueryForDebugging() throws Exception {
        AiPlanDayService service = new AiPlanDayService(
                Mockito.mock(SpaceService.class),
                Mockito.mock(TravelPlanDayMapper.class),
                Mockito.mock(AiAgentRunMapper.class),
                Mockito.mock(AiAgentEventMapper.class),
                Mockito.mock(AiPlanDayDraftMapper.class),
                Mockito.mock(AppUserMapper.class),
                Mockito.mock(AiTravelMemorySyncService.class),
                new ObjectMapper(),
                Mockito.mock(StringRedisTemplate.class),
                "http://127.0.0.1:8000");

        AiTravelMemorySyncService.MemorySearchResult memorySearchResult =
                new AiTravelMemorySyncService.MemorySearchResult(
                        true,
                        List.of(Map.of(
                                "memoryId", "trip_post_1",
                                "sourceType", "TRIP_POST",
                                "cityName", "青岛",
                                "content", "去海边看日落",
                                "score", 0.12,
                                "reason", "城市匹配：青岛")),
                        "",
                        "Intent: 检索历史旅行偏好\nDestination: 青岛");

        Method method = AiPlanDayService.class.getDeclaredMethod(
                "buildMemoryToolResult", AiTravelMemorySyncService.MemorySearchResult.class);
        method.setAccessible(true);

        Map<String, Object> result = (Map<String, Object>) method.invoke(service, memorySearchResult);
        Map<String, Object> data = (Map<String, Object>) result.get("data");

        assertEquals("Intent: 检索历史旅行偏好\nDestination: 青岛", data.get("query"));
        assertEquals(1, ((List<?>) data.get("topMemories")).size());
    }
}
