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
}
