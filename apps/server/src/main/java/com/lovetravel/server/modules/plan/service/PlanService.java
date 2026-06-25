package com.lovetravel.server.modules.plan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lovetravel.server.common.ApiException;
import com.lovetravel.server.modules.plan.mapper.TravelPlanDayMapper;
import com.lovetravel.server.modules.space.domain.CoupleSpace;
import com.lovetravel.server.modules.plan.domain.TravelPlanDay;
import com.lovetravel.server.modules.space.service.SpaceService;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.lovetravel.server.modules.plan.dto.PlanDayRequest;
import com.lovetravel.server.modules.plan.vo.PlanDayResponse;

@Service
public class PlanService {

    private final TravelPlanDayMapper planDayMapper;
    private final SpaceService spaceService;

    public PlanService(TravelPlanDayMapper planDayMapper, SpaceService spaceService) {
        this.planDayMapper = planDayMapper;
        this.spaceService = spaceService;
    }

    @Transactional
    public List<PlanDayResponse> listDays(Long userId) {
        CoupleSpace space = spaceService.requireActiveSpace(userId);
        return planDayMapper.selectList(new LambdaQueryWrapper<TravelPlanDay>()
                        .eq(TravelPlanDay::getSpaceId, space.getId())
                        .eq(TravelPlanDay::getDeleted, 0)
                        .orderByAsc(TravelPlanDay::getSortOrder)
                        .orderByAsc(TravelPlanDay::getId))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public PlanDayResponse createDay(PlanDayRequest request) {
        CoupleSpace space = spaceService.requireEditableActiveSpace(request.getUserId());

        TravelPlanDay day = new TravelPlanDay();
        day.setSpaceId(space.getId());
        day.setPlanDate(parseDate(request.getDate()));
        day.setTitle(cleanText(request.getTitle()));
        day.setDetail(cleanText(request.getDetail()));
        day.setSortOrder(nextSortOrder(space.getId()));
        day.setCreatedByUserId(request.getUserId());
        day.setUpdatedByUserId(request.getUserId());
        day.setDeleted(0);
        planDayMapper.insert(day);
        return toResponse(day);
    }

    @Transactional
    public PlanDayResponse updateDay(Long dayId, PlanDayRequest request) {
        CoupleSpace space = spaceService.requireEditableActiveSpace(request.getUserId());
        TravelPlanDay day = requirePlanDay(dayId, space.getId());
        day.setPlanDate(parseDate(request.getDate()));
        day.setTitle(cleanText(request.getTitle()));
        day.setDetail(cleanText(request.getDetail()));
        day.setUpdatedByUserId(request.getUserId());
        planDayMapper.updateById(day);
        return toResponse(planDayMapper.selectById(dayId));
    }

    @Transactional
    public void deleteDay(Long dayId, Long userId) {
        CoupleSpace space = spaceService.requireEditableActiveSpace(userId);
        TravelPlanDay day = requirePlanDay(dayId, space.getId());
        day.setDeleted(1);
        day.setUpdatedByUserId(userId);
        planDayMapper.updateById(day);
    }

    private TravelPlanDay requirePlanDay(Long dayId, Long spaceId) {
        TravelPlanDay day = planDayMapper.selectById(dayId);
        if (day == null || Integer.valueOf(1).equals(day.getDeleted())) {
            throw new ApiException("计划不存在或已删除");
        }
        if (!spaceId.equals(day.getSpaceId())) {
            throw new ApiException("当前用户无权修改这一天计划");
        }
        return day;
    }

    private Integer nextSortOrder(Long spaceId) {
        return planDayMapper.selectList(new LambdaQueryWrapper<TravelPlanDay>()
                        .eq(TravelPlanDay::getSpaceId, spaceId)
                        .eq(TravelPlanDay::getDeleted, 0))
                .stream()
                .map(TravelPlanDay::getSortOrder)
                .filter(value -> value != null)
                .max(Comparator.naturalOrder())
                .orElse(0) + 1;
    }

    private LocalDate parseDate(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException exception) {
            throw new ApiException("计划日期格式不正确");
        }
    }

    private String cleanText(String value) {
        return value == null ? "" : value.trim();
    }

    private PlanDayResponse toResponse(TravelPlanDay day) {
        return new PlanDayResponse(
                day.getId(),
                day.getPlanDate() == null ? "" : day.getPlanDate().toString(),
                day.getTitle() == null ? "" : day.getTitle(),
                day.getDetail() == null ? "" : day.getDetail(),
                day.getSortOrder());
    }
}
