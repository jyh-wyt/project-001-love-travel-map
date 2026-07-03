package com.lovetravel.server.modules.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lovetravel.server.common.ApiException;
import com.lovetravel.server.modules.ai.mapper.AiAgentEventMapper;
import com.lovetravel.server.modules.ai.mapper.AiAgentRunMapper;
import com.lovetravel.server.modules.ai.mapper.AiPlanDayDraftMapper;
import com.lovetravel.server.modules.auth.mapper.AppUserMapper;
import com.lovetravel.server.modules.plan.mapper.TravelPlanDayMapper;
import com.lovetravel.server.modules.ai.domain.AiAgentEvent;
import com.lovetravel.server.modules.ai.domain.AiAgentRun;
import com.lovetravel.server.modules.ai.domain.AiPlanDayDraft;
import com.lovetravel.server.modules.auth.domain.AppUser;
import com.lovetravel.server.modules.space.domain.CoupleSpace;
import com.lovetravel.server.modules.plan.domain.TravelPlanDay;
import com.lovetravel.server.modules.plan.vo.PlanDayResponse;
import com.lovetravel.server.modules.space.service.SpaceService;
import java.io.BufferedReader;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.lovetravel.server.modules.ai.vo.AiPlanDayApplyResponse;
import com.lovetravel.server.modules.ai.dto.AiPlanDayGenerateRequest;
import com.lovetravel.server.modules.ai.service.AiTravelMemorySyncService.SyncResult;

@Service
public class AiPlanDayService {

    private static final String AGENT_TYPE = "TRAVEL_DAY_PLAN";
    private static final String PROMPT_VERSION = "travel_day_plan_v1";
    private static final String MODEL_NAME = "qwen-plus";
    private static final Set<String> COUNTED_STATUSES = Set.of("SUCCESS", "APPLIED", "DISCARDED");
    private static final ExecutorService SSE_EXECUTOR = Executors.newCachedThreadPool();
    private static final String AI_GENERATING_KEY_PREFIX = "love-travel:ai:plan-day:generating:";
    private static final String AI_RATE_KEY_PREFIX = "love-travel:ai:plan-day:rate:";
    private static final Duration AI_GENERATING_LOCK_TTL = Duration.ofMinutes(3);
    private static final Duration AI_RATE_WINDOW = Duration.ofMinutes(1);
    private static final int AI_RATE_LIMIT_PER_MINUTE = 3;

    private final SpaceService spaceService;
    private final TravelPlanDayMapper planDayMapper;
    private final AiAgentRunMapper runMapper;
    private final AiAgentEventMapper eventMapper;
    private final AiPlanDayDraftMapper draftMapper;
    private final AppUserMapper appUserMapper;
    private final AiTravelMemorySyncService memorySyncService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String aiServiceBaseUrl;
    private final StringRedisTemplate redisTemplate;

    public AiPlanDayService(
            SpaceService spaceService,
            TravelPlanDayMapper planDayMapper,
            AiAgentRunMapper runMapper,
            AiAgentEventMapper eventMapper,
            AiPlanDayDraftMapper draftMapper,
            AppUserMapper appUserMapper,
            AiTravelMemorySyncService memorySyncService,
            ObjectMapper objectMapper,
            StringRedisTemplate redisTemplate,
            @Value("${love-travel.ai-service.base-url}") String aiServiceBaseUrl) {
        this.spaceService = spaceService;
        this.planDayMapper = planDayMapper;
        this.runMapper = runMapper;
        this.eventMapper = eventMapper;
        this.draftMapper = draftMapper;
        this.appUserMapper = appUserMapper;
        this.memorySyncService = memorySyncService;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.aiServiceBaseUrl = aiServiceBaseUrl;
    }

    @Transactional
    public SseEmitter generatePlanDay(Long userId, Long dayId, AiPlanDayGenerateRequest request) {
        SseEmitter emitter = new SseEmitter(180_000L);
        CoupleSpace space;
        TravelPlanDay day;
        SyncResult memorySyncResult;
        boolean lockAcquired = false;
        try {
            space = spaceService.requireEditableActiveSpace(userId);
            day = requirePlanDay(dayId, space.getId());
            validateRequest(request);
            ensureShortWindowRateLimit(userId);
            ensureQuota(userId);
            acquireGenerateLock(userId, dayId);
            lockAcquired = true;
            memorySyncResult = memorySyncService.syncSpaceMemoriesBestEffort(space.getId());
        } catch (ApiException exception) {
            if (lockAcquired) {
                releaseGenerateLock(userId, dayId);
            }
            sendError(emitter, exception.getMessage());
            return emitter;
        }

        try {
            String runId = "ai_run_" + UUID.randomUUID().toString().replace("-", "");
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("dayId", dayId);
            input.put("destination", request.getDestination());
            input.put("places", request.getPlaces());
            input.put("mustVisitPlaces", request.getMustVisitPlaces() == null ? List.of() : request.getMustVisitPlaces());
            input.put("morningMode", request.getMorningMode());
            input.put("afternoonMode", request.getAfternoonMode());
            input.put("eveningMode", request.getEveningMode());
            input.put("notes", request.getNotes() == null ? "" : request.getNotes());
            input.put("regenerateMode", normalizeRegenerateMode(request.getRegenerateMode()));
            input.put("sourceDraftId", request.getSourceDraftId() == null ? "" : request.getSourceDraftId().toString());
            String inputJson = toJson(input);

            AiAgentRun run = new AiAgentRun();
            run.setRunId(runId);
            run.setSpaceId(space.getId());
            run.setUserId(userId);
            run.setAgentType(AGENT_TYPE);
            run.setModelName(MODEL_NAME);
            run.setPromptVersion(PROMPT_VERSION);
            run.setInputJson(inputJson);
            run.setStatus("RUNNING");
            run.setTokenInput(0);
            run.setTokenOutput(0);
            run.setDurationMs(0);
            run.setAccepted(0);
            run.setDeleted(0);
            runMapper.insert(run);

            SSE_EXECUTOR.execute(() -> streamFromPython(emitter, runId, userId, space, day, request, memorySyncResult));
            return emitter;
        } catch (RuntimeException exception) {
            releaseGenerateLock(userId, dayId);
            throw exception;
        }
    }

    @Transactional
    public AiPlanDayApplyResponse applyDraft(Long userId, Long draftId) {
        CoupleSpace space = spaceService.requireEditableActiveSpace(userId);
        AiPlanDayDraft draft = requireDraft(draftId, space.getId());
        if (!"DRAFT".equals(draft.getStatus())) {
            throw new ApiException("该 AI 草稿已经处理过");
        }

        TravelPlanDay day = requirePlanDay(draft.getPlanDayId(), space.getId());
        day.setTitle(draft.getTitle());
        day.setDetail(formatDraftDetail(draft));
        day.setUpdatedByUserId(userId);
        planDayMapper.updateById(day);

        draft.setStatus("APPLIED");
        draft.setAppliedAt(LocalDateTime.now());
        draftMapper.updateById(draft);

        AiAgentRun run = findRun(draft.getRunId());
        if (run != null) {
            run.setStatus("APPLIED");
            run.setAccepted(1);
            runMapper.updateById(run);
        }

        TravelPlanDay updated = planDayMapper.selectById(day.getId());
        return new AiPlanDayApplyResponse(true, toPlanDayResponse(updated));
    }

    @Transactional
    public void discardDraft(Long userId, Long draftId) {
        CoupleSpace space = spaceService.requireActiveSpace(userId);
        AiPlanDayDraft draft = requireDraft(draftId, space.getId());
        if (!"DRAFT".equals(draft.getStatus())) {
            return;
        }
        draft.setStatus("DISCARDED");
        draftMapper.updateById(draft);

        AiAgentRun run = findRun(draft.getRunId());
        if (run != null && !"APPLIED".equals(run.getStatus())) {
            run.setStatus("DISCARDED");
            runMapper.updateById(run);
        }
    }

    private void streamFromPython(SseEmitter emitter, String runId, Long userId, CoupleSpace space, TravelPlanDay day, AiPlanDayGenerateRequest request, SyncResult memorySyncResult) {
        long startedAt = System.currentTimeMillis();
        try {
            sendAgentStep(emitter, runId, "MEMORY_SYNC", memorySyncResult.success() ? "done" : "skipped",
                    memorySyncResult.success()
                            ? "已同步旅行记忆：" + memorySyncResult.changedCount() + " 条更新，" + memorySyncResult.skippedCount() + " 条跳过"
                            : "旅行记忆同步暂时不可用，继续生成计划");

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("requestId", runId);
            payload.put("spaceId", space.getId());
            payload.put("userId", userId);
            payload.put("planDayId", day.getId());
            payload.put("modelName", MODEL_NAME);
            payload.put("promptVersion", PROMPT_VERSION);
            payload.put("destination", request.getDestination());
            payload.put("planDate", day.getPlanDate() == null ? "" : day.getPlanDate().toString());
            payload.put("places", request.getPlaces());
            payload.put("mustVisitPlaces", request.getMustVisitPlaces() == null ? List.of() : request.getMustVisitPlaces());
            payload.put("morningMode", request.getMorningMode());
            payload.put("afternoonMode", request.getAfternoonMode());
            payload.put("eveningMode", request.getEveningMode());
            payload.put("notes", request.getNotes() == null ? "" : request.getNotes());
            payload.put("regenerateMode", normalizeRegenerateMode(request.getRegenerateMode()));
            payload.put("sourceDraft", loadSourceDraftJson(request.getSourceDraftId(), space.getId()));
            sendAgentStep(emitter, runId, "MEMORY_RETRIEVAL", "running", "正在从向量库检索相关旅行记忆");
            List<Map<String, Object>> travelMemories = memorySyncService.searchPlanMemoriesBestEffort(space.getId(), request);
            payload.put("travelMemories", travelMemories);
            sendAgentStep(emitter, runId, "MEMORY_RETRIEVAL", travelMemories.isEmpty() ? "skipped" : "done",
                    travelMemories.isEmpty() ? "没有匹配到可参考的历史记忆" : "已检索到 " + travelMemories.size() + " 条相关旅行记忆");
            sendMemories(emitter, runId, travelMemories);
            sendAgentStep(emitter, runId, "PLAN_GENERATION", "running", "正在调用大模型生成当天计划");

            HttpRequest pythonRequest = HttpRequest.newBuilder()
                    .uri(URI.create(aiServiceBaseUrl + "/internal/ai/plan-day/generate-stream"))
                    .version(HttpClient.Version.HTTP_1_1)
                    .timeout(Duration.ofMinutes(3))
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(toJson(payload)))
                    .build();

            HttpResponse<java.util.stream.Stream<String>> response = httpClient.send(pythonRequest, HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() >= 400) {
                throw new ApiException("AI 服务暂时不可用");
            }

            BufferedSseEvent current = new BufferedSseEvent();
            try (java.util.stream.Stream<String> lines = response.body()) {
                lines.forEach(line -> handlePythonLine(emitter, runId, userId, space, day, current, line));
            }

            AiAgentRun run = findRun(runId);
            if (run != null && "RUNNING".equals(run.getStatus())) {
                run.setDurationMs((int) (System.currentTimeMillis() - startedAt));
                runMapper.updateById(run);
            }
            emitter.complete();
        } catch (Exception exception) {
            markRunFailed(runId, exception.getMessage());
            sendError(emitter, exception.getMessage() == null ? "AI 生成失败" : exception.getMessage());
        } finally {
            releaseGenerateLock(userId, day.getId());
        }
    }

    private void handlePythonLine(SseEmitter emitter, String runId, Long userId, CoupleSpace space, TravelPlanDay day, BufferedSseEvent current, String line) {
        try {
            if (line == null || line.isBlank()) {
                dispatchPythonEvent(emitter, runId, userId, space, day, current);
                current.clear();
                return;
            }
            if (line.startsWith("event:")) {
                current.event = line.substring("event:".length()).trim();
            } else if (line.startsWith("data:")) {
                current.data = line.substring("data:".length()).trim();
            }
        } catch (Exception exception) {
            throw new ApiException(exception.getMessage());
        }
    }

    private void dispatchPythonEvent(SseEmitter emitter, String runId, Long userId, CoupleSpace space, TravelPlanDay day, BufferedSseEvent event) throws IOException {
        if (event.event == null || event.data == null) {
            return;
        }
        if ("progress".equals(event.event)) {
            recordEvent(runId, "PROGRESS", event.data, event.data);
            emitter.send(SseEmitter.event().name("progress").data(event.data));
            return;
        }
        if ("draft".equals(event.event)) {
            sendAgentStep(emitter, runId, "PLAN_GENERATION", "done", "大模型已返回计划草稿");
            String data = saveDraftAndEnrichEvent(runId, userId, space, day, event.data);
            sendAgentStep(emitter, runId, "DRAFT_SAVE", "done", "AI 草稿已保存，可以应用到这一天");
            emitter.send(SseEmitter.event().name("draft").data(data));
            return;
        }
        if ("error".equals(event.event)) {
            markRunFailed(runId, event.data);
            sendAgentStep(emitter, runId, "PLAN_GENERATION", "failed", "AI 生成失败，请稍后重试");
            emitter.send(SseEmitter.event().name("error").data(event.data));
        }
    }

    private void sendMemories(SseEmitter emitter, String runId, List<Map<String, Object>> travelMemories) throws IOException {
        String data = toJson(Map.of("items", travelMemories));
        recordEvent(runId, "MEMORIES", travelMemories.isEmpty() ? "AI 未检索到历史记忆" : "AI 已检索到历史记忆", data);
        emitter.send(SseEmitter.event().name("memories").data(data));
    }

    private void sendAgentStep(SseEmitter emitter, String runId, String step, String status, String message) throws IOException {
        String data = toJson(Map.of(
                "step", step,
                "status", status,
                "message", message
        ));
        recordEvent(runId, "AGENT_STEP", message, data);
        emitter.send(SseEmitter.event().name("agent-step").data(data));
    }

    private String saveDraftAndEnrichEvent(String runId, Long userId, CoupleSpace space, TravelPlanDay day, String data) {
        try {
            JsonNode node = objectMapper.readTree(data);
            AiPlanDayDraft draft = new AiPlanDayDraft();
            draft.setRunId(runId);
            draft.setSpaceId(space.getId());
            draft.setUserId(userId);
            draft.setPlanDayId(day.getId());
            draft.setTitle(node.path("title").asText("AI 旅行计划"));
            draft.setMorningJson(node.path("morning").toString());
            draft.setAfternoonJson(node.path("afternoon").toString());
            draft.setEveningJson(node.path("evening").toString());
            draft.setRecommendationsJson(node.path("recommendations").toString());
            draft.setTipsJson(node.path("reminders").toString());
            draft.setStatus("DRAFT");
            draft.setDeleted(0);
            draftMapper.insert(draft);

            AiAgentRun run = findRun(runId);
            if (run != null) {
                run.setOutputJson(data);
                run.setStatus("SUCCESS");
                runMapper.updateById(run);
            }
            recordEvent(runId, "DRAFT", "AI 计划草稿已生成", data);

            ObjectNode enriched = node.deepCopy();
            enriched.put("runId", runId);
            enriched.put("draftId", draft.getId());
            enriched.put("dayId", day.getId());
            return enriched.toString();
        } catch (Exception exception) {
            throw new ApiException("AI 返回格式不正确");
        }
    }

    private void ensureQuota(Long userId) {
        AppUser user = appUserMapper.selectById(userId);
        String memberLevel = user == null || user.getMemberLevel() == null ? "FREE" : user.getMemberLevel();
        int limit = "VIP".equalsIgnoreCase(memberLevel) || "ADMIN".equalsIgnoreCase(memberLevel) ? 20 : 3;
        LocalDateTime since = LocalDateTime.now().minusDays(30);
        Long used = runMapper.selectCount(new LambdaQueryWrapper<AiAgentRun>()
                .eq(AiAgentRun::getUserId, userId)
                .eq(AiAgentRun::getAgentType, AGENT_TYPE)
                .eq(AiAgentRun::getDeleted, 0)
                .ge(AiAgentRun::getCreatedAt, since)
                .in(AiAgentRun::getStatus, COUNTED_STATUSES));
        if (used >= limit) {
            throw new ApiException("近 30 天 AI 规划次数已用完");
        }
    }

    private void ensureShortWindowRateLimit(Long userId) {
        String key = AI_RATE_KEY_PREFIX + userId;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, AI_RATE_WINDOW);
        }
        if (count != null && count > AI_RATE_LIMIT_PER_MINUTE) {
            throw new ApiException("AI 规划请求太频繁，请稍后再试");
        }
    }

    private void acquireGenerateLock(Long userId, Long dayId) {
        String key = generateLockKey(userId, dayId);
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(key, "1", AI_GENERATING_LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            throw new ApiException("这一天的 AI 计划正在生成中，请不要重复点击");
        }
    }

    private void releaseGenerateLock(Long userId, Long dayId) {
        redisTemplate.delete(generateLockKey(userId, dayId));
    }

    private String generateLockKey(Long userId, Long dayId) {
        return AI_GENERATING_KEY_PREFIX + userId + ":" + dayId;
    }

    private void validateRequest(AiPlanDayGenerateRequest request) {
        if (!List.of("PLAY", "REST").contains(request.getMorningMode())
                || !List.of("PLAY", "REST").contains(request.getAfternoonMode())
                || !List.of("PLAY", "REST").contains(request.getEveningMode())) {
            throw new ApiException("上午、下午、晚上只能选择出去玩或酒店休息");
        }
        List<String> must = request.getMustVisitPlaces() == null ? List.of() : request.getMustVisitPlaces();
        for (String place : must) {
            if (!request.getPlaces().contains(place)) {
                throw new ApiException("必去地点只能从已添加地点中选择");
            }
        }
    }

    private TravelPlanDay requirePlanDay(Long dayId, Long spaceId) {
        TravelPlanDay day = planDayMapper.selectById(dayId);
        if (day == null || Integer.valueOf(1).equals(day.getDeleted()) || !spaceId.equals(day.getSpaceId())) {
            throw new ApiException("计划不存在或无权访问");
        }
        return day;
    }

    private AiPlanDayDraft requireDraft(Long draftId, Long spaceId) {
        AiPlanDayDraft draft = draftMapper.selectById(draftId);
        if (draft == null || Integer.valueOf(1).equals(draft.getDeleted()) || !spaceId.equals(draft.getSpaceId())) {
            throw new ApiException("AI 草稿不存在或无权访问");
        }
        return draft;
    }

    private String loadSourceDraftJson(Long sourceDraftId, Long spaceId) {
        if (sourceDraftId == null) {
            return "";
        }
        AiPlanDayDraft draft = requireDraft(sourceDraftId, spaceId);
        return toJson(Map.of(
                "title", draft.getTitle(),
                "morning", draft.getMorningJson() == null ? "" : draft.getMorningJson(),
                "afternoon", draft.getAfternoonJson() == null ? "" : draft.getAfternoonJson(),
                "evening", draft.getEveningJson() == null ? "" : draft.getEveningJson(),
                "recommendations", draft.getRecommendationsJson() == null ? "" : draft.getRecommendationsJson(),
                "reminders", draft.getTipsJson() == null ? "" : draft.getTipsJson()
        ));
    }

    private String formatDraftDetail(AiPlanDayDraft draft) {
        try {
            JsonNode morning = objectMapper.readTree(draft.getMorningJson());
            JsonNode afternoon = objectMapper.readTree(draft.getAfternoonJson());
            JsonNode evening = objectMapper.readTree(draft.getEveningJson());
            JsonNode recommendations = objectMapper.readTree(draft.getRecommendationsJson());
            JsonNode reminders = objectMapper.readTree(draft.getTipsJson());
            StringBuilder builder = new StringBuilder();
            builder.append("上午：").append(morning.path("content").asText()).append("\n\n");
            builder.append("下午：").append(afternoon.path("content").asText()).append("\n\n");
            builder.append("晚上：").append(evening.path("content").asText()).append("\n\n");
            builder.append("附近推荐：\n");
            for (JsonNode item : recommendations) {
                builder.append(item.path("title").asText()).append("：");
                for (JsonNode recommendation : item.path("items")) {
                    builder.append(recommendation.asText()).append("；");
                }
                builder.append("\n");
            }
            builder.append("\n提醒：");
            for (JsonNode reminder : reminders) {
                builder.append(reminder.asText()).append("；");
            }
            return builder.toString().trim();
        } catch (Exception exception) {
            throw new ApiException("AI 草稿格式不正确");
        }
    }

    private AiAgentRun findRun(String runId) {
        return runMapper.selectOne(new LambdaQueryWrapper<AiAgentRun>().eq(AiAgentRun::getRunId, runId).last("LIMIT 1"));
    }

    private void recordEvent(String runId, String type, String message, String json) {
        AiAgentEvent event = new AiAgentEvent();
        event.setRunId(runId);
        event.setEventType(type);
        event.setEventMessage(message.length() > 500 ? message.substring(0, 500) : message);
        event.setEventJson(json);
        eventMapper.insert(event);
    }

    private void markRunFailed(String runId, String message) {
        AiAgentRun run = findRun(runId);
        if (run != null) {
            run.setStatus("FAILED");
            run.setErrorMessage(message == null ? "AI 生成失败" : message);
            runMapper.updateById(run);
        }
    }

    private void sendError(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().name("error").data(toJson(Map.of("message", message))));
        } catch (IOException ignored) {
            // The client may have closed the stream.
        }
        emitter.complete();
    }

    private String normalizeRegenerateMode(String value) {
        if ("REVISE".equals(value) || "REWRITE".equals(value)) {
            return value;
        }
        return "NEW";
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new ApiException("JSON 序列化失败");
        }
    }

    private PlanDayResponse toPlanDayResponse(TravelPlanDay day) {
        return new PlanDayResponse(
                day.getId(),
                day.getPlanDate() == null ? "" : day.getPlanDate().toString(),
                day.getTitle() == null ? "" : day.getTitle(),
                day.getDetail() == null ? "" : day.getDetail(),
                day.getSortOrder());
    }

    private static class BufferedSseEvent {
        private String event;
        private String data;

        private void clear() {
            event = null;
            data = null;
        }
    }
}
