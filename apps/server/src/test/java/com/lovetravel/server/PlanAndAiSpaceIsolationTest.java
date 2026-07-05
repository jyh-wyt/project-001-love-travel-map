package com.lovetravel.server;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lovetravel.server.common.ApiException;
import com.lovetravel.server.modules.ai.domain.AiPlanDayDraft;
import com.lovetravel.server.modules.ai.domain.AiAgentRun;
import com.lovetravel.server.modules.ai.mapper.AiAgentEventMapper;
import com.lovetravel.server.modules.ai.mapper.AiAgentRunMapper;
import com.lovetravel.server.modules.ai.mapper.AiPlanDayDraftMapper;
import com.lovetravel.server.modules.ai.service.AiPlanDayService;
import com.lovetravel.server.modules.ai.service.AiTravelMemorySyncService;
import com.lovetravel.server.modules.auth.mapper.AppUserMapper;
import com.lovetravel.server.modules.plan.domain.TravelPlanDay;
import com.lovetravel.server.modules.plan.dto.PlanDayRequest;
import com.lovetravel.server.modules.plan.mapper.TravelPlanDayMapper;
import com.lovetravel.server.modules.plan.service.PlanService;
import com.lovetravel.server.modules.space.domain.CoupleSpace;
import com.lovetravel.server.modules.space.service.SpaceService;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;

class PlanAndAiSpaceIsolationTest {

    private SpaceService spaceService;
    private TravelPlanDayMapper planDayMapper;
    private AiPlanDayDraftMapper draftMapper;
    private AiAgentRunMapper runMapper;
    private AiAgentEventMapper eventMapper;

    @BeforeEach
    void setUp() {
        initTableInfo(TravelPlanDay.class);
        initTableInfo(AiPlanDayDraft.class);
        spaceService = Mockito.mock(SpaceService.class);
        planDayMapper = Mockito.mock(TravelPlanDayMapper.class);
        draftMapper = Mockito.mock(AiPlanDayDraftMapper.class);
        runMapper = Mockito.mock(AiAgentRunMapper.class);
        eventMapper = Mockito.mock(AiAgentEventMapper.class);
    }

    @Test
    void listPlanDaysReadsOnlyCurrentSpace() {
        Mockito.when(spaceService.requireActiveSpace(7L)).thenReturn(space(11L));
        Mockito.when(planDayMapper.selectList(any())).thenReturn(List.of());
        PlanService planService = new PlanService(planDayMapper, spaceService, new ObjectMapper());

        planService.listDays(7L);

        ArgumentCaptor<LambdaQueryWrapper<TravelPlanDay>> queryCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        Mockito.verify(planDayMapper).selectList(queryCaptor.capture());
        assertQueryContainsSpaceId(queryCaptor.getValue());
    }

    @Test
    void updatePlanDayRejectsOtherSpaceDay() {
        Mockito.when(spaceService.requireEditableActiveSpace(7L)).thenReturn(space(11L));
        TravelPlanDay otherSpaceDay = new TravelPlanDay();
        otherSpaceDay.setId(22L);
        otherSpaceDay.setSpaceId(99L);
        otherSpaceDay.setDeleted(0);
        Mockito.when(planDayMapper.selectById(22L)).thenReturn(otherSpaceDay);
        PlanService planService = new PlanService(planDayMapper, spaceService, new ObjectMapper());

        PlanDayRequest request = new PlanDayRequest();
        request.setUserId(7L);
        request.setDate("2026-07-05");
        request.setTitle("测试");
        request.setDetail("测试");

        assertThrows(ApiException.class, () -> planService.updateDay(22L, request));
    }

    @Test
    void listAiDraftHistoryRejectsOtherSpaceDay() {
        Mockito.when(spaceService.requireActiveSpace(7L)).thenReturn(space(11L));
        TravelPlanDay otherSpaceDay = new TravelPlanDay();
        otherSpaceDay.setId(22L);
        otherSpaceDay.setSpaceId(99L);
        otherSpaceDay.setDeleted(0);
        Mockito.when(planDayMapper.selectById(22L)).thenReturn(otherSpaceDay);

        assertThrows(ApiException.class, () -> aiService().listDraftHistory(7L, 22L));
    }

    @Test
    void applyAiDraftRejectsOtherSpaceDraft() {
        Mockito.when(spaceService.requireEditableActiveSpace(7L)).thenReturn(space(11L));
        AiPlanDayDraft otherSpaceDraft = new AiPlanDayDraft();
        otherSpaceDraft.setId(33L);
        otherSpaceDraft.setSpaceId(99L);
        otherSpaceDraft.setDeleted(0);
        otherSpaceDraft.setStatus("DRAFT");
        Mockito.when(draftMapper.selectById(33L)).thenReturn(otherSpaceDraft);

        assertThrows(ApiException.class, () -> aiService().applyDraft(7L, 33L));
    }

    @Test
    void listAiRunEventsRejectsOtherSpaceRun() {
        Mockito.when(spaceService.requireActiveSpace(7L)).thenReturn(space(11L));
        AiAgentRun otherSpaceRun = new AiAgentRun();
        otherSpaceRun.setRunId("ai_run_other");
        otherSpaceRun.setSpaceId(99L);
        otherSpaceRun.setDeleted(0);
        Mockito.when(runMapper.selectOne(any())).thenReturn(otherSpaceRun);

        assertThrows(ApiException.class, () -> aiService().listRunEvents(7L, "ai_run_other"));
    }

    private AiPlanDayService aiService() {
        return new AiPlanDayService(
                spaceService,
                planDayMapper,
                runMapper,
                eventMapper,
                draftMapper,
                Mockito.mock(AppUserMapper.class),
                Mockito.mock(AiTravelMemorySyncService.class),
                new ObjectMapper(),
                Mockito.mock(StringRedisTemplate.class),
                "http://127.0.0.1:8000");
    }

    private CoupleSpace space(Long spaceId) {
        CoupleSpace space = new CoupleSpace();
        space.setId(spaceId);
        return space;
    }

    private void assertQueryContainsSpaceId(LambdaQueryWrapper<?> wrapper) {
        assertTrue(wrapper.getCustomSqlSegment().contains("space_id"), wrapper.getCustomSqlSegment());
    }

    private void initTableInfo(Class<?> entityClass) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), entityClass.getName());
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}
