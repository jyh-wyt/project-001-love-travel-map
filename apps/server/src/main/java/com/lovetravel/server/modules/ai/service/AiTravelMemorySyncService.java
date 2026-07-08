package com.lovetravel.server.modules.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lovetravel.server.common.ApiException;
import com.lovetravel.server.modules.ai.domain.AiTravelMemoryIndex;
import com.lovetravel.server.modules.ai.dto.AiPlanDayGenerateRequest;
import com.lovetravel.server.modules.ai.mapper.AiTravelMemoryIndexMapper;
import com.lovetravel.server.modules.ai.service.AiTravelMemoryDocumentFactory.MemoryDocument;
import com.lovetravel.server.modules.plan.domain.TravelPlanDay;
import com.lovetravel.server.modules.plan.mapper.TravelPlanDayMapper;
import com.lovetravel.server.modules.travel.domain.Trip;
import com.lovetravel.server.modules.travel.domain.TripPost;
import com.lovetravel.server.modules.travel.mapper.TripMapper;
import com.lovetravel.server.modules.travel.mapper.TripPostMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AiTravelMemorySyncService {

    private static final Logger log = LoggerFactory.getLogger(AiTravelMemorySyncService.class);
    private static final int PLAN_MEMORY_TOP_K = 3;

    private final TripPostMapper tripPostMapper;
    private final TripMapper tripMapper;
    private final TravelPlanDayMapper planDayMapper;
    private final AiTravelMemoryIndexMapper memoryIndexMapper;
    private final AiTravelMemoryDocumentFactory documentFactory;
    private final AiTravelMemoryReasoner memoryReasoner;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String aiServiceBaseUrl;

    public AiTravelMemorySyncService(
            TripPostMapper tripPostMapper,
            TripMapper tripMapper,
            TravelPlanDayMapper planDayMapper,
            AiTravelMemoryIndexMapper memoryIndexMapper,
            AiTravelMemoryDocumentFactory documentFactory,
            ObjectMapper objectMapper,
            @Value("${love-travel.ai-service.base-url}") String aiServiceBaseUrl) {
        this.tripPostMapper = tripPostMapper;
        this.tripMapper = tripMapper;
        this.planDayMapper = planDayMapper;
        this.memoryIndexMapper = memoryIndexMapper;
        this.documentFactory = documentFactory;
        this.memoryReasoner = new AiTravelMemoryReasoner();
        this.objectMapper = objectMapper;
        this.aiServiceBaseUrl = aiServiceBaseUrl;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public SyncResult syncSpaceMemoriesBestEffort(Long spaceId) {
        try {
            return syncSpaceMemories(spaceId);
        } catch (Exception exception) {
            log.warn("AI memory sync skipped for spaceId={}: {}", spaceId, exception.getMessage());
            return new SyncResult(0, 0, false);
        }
    }

    public MemorySearchResult searchPlanMemoriesBestEffort(Long spaceId, AiPlanDayGenerateRequest request) {
        try {
            String query = documentFactory.buildPlanSearchQuery(
                    request.getDestination(),
                    request.getPlaces(),
                    request.getMustVisitPlaces(),
                    request.getHotelLocation(),
                    request.getNotes());
            if (query.isBlank()) {
                return new MemorySearchResult(true, List.of(), "", query);
            }
            List<Map<String, Object>> memories = callPythonSearch(spaceId, query);
            enrichMemoryReasons(request, memories);
            return new MemorySearchResult(true, memories, "", query);
        } catch (Exception exception) {
            log.warn("AI memory search skipped for spaceId={}: {}", spaceId, exception.getMessage());
            return new MemorySearchResult(false, List.of(), "记忆检索暂时不可用，本次将不参考历史记忆", "");
        }
    }

    public SyncResult syncSpaceMemories(Long spaceId) {
        List<MemoryDocument> documents = loadMemoryDocuments(spaceId);
        List<MemoryDocument> changedDocuments = documents.stream()
                .filter(this::isChanged)
                .toList();
        if (changedDocuments.isEmpty()) {
            return new SyncResult(0, documents.size(), true);
        }

        boolean stored = callPythonUpsert(changedDocuments);
        if (!stored) {
            return new SyncResult(0, documents.size(), false);
        }
        changedDocuments.forEach(this::upsertIndex);
        return new SyncResult(changedDocuments.size(), documents.size() - changedDocuments.size(), true);
    }

    private List<MemoryDocument> loadMemoryDocuments(Long spaceId) {
        List<MemoryDocument> documents = new ArrayList<>();
        Map<Long, Trip> tripsById = loadTripsById(spaceId);

        List<TripPost> posts = tripPostMapper.selectList(new LambdaQueryWrapper<TripPost>()
                .eq(TripPost::getSpaceId, spaceId)
                .eq(TripPost::getDeleted, 0)
                .orderByDesc(TripPost::getUpdatedAt));
        for (TripPost post : posts) {
            if (isBlank(post.getContent()) && isBlank(post.getPolishedContent())) {
                continue;
            }
            documents.add(documentFactory.fromTripPost(post, tripsById.get(post.getTripId())));
        }

        List<TravelPlanDay> planDays = planDayMapper.selectList(new LambdaQueryWrapper<TravelPlanDay>()
                .eq(TravelPlanDay::getSpaceId, spaceId)
                .eq(TravelPlanDay::getDeleted, 0)
                .orderByAsc(TravelPlanDay::getSortOrder));
        for (TravelPlanDay day : planDays) {
            documentFactory.fromPlanDay(day).ifPresent(documents::add);
        }

        return documents;
    }

    private Map<Long, Trip> loadTripsById(Long spaceId) {
        List<Trip> trips = tripMapper.selectList(new LambdaQueryWrapper<Trip>()
                .eq(Trip::getSpaceId, spaceId)
                .eq(Trip::getDeleted, 0));
        Map<Long, Trip> result = new LinkedHashMap<>();
        for (Trip trip : trips) {
            result.put(trip.getId(), trip);
        }
        return result;
    }

    private boolean isChanged(MemoryDocument document) {
        AiTravelMemoryIndex index = findIndex(document.memoryId());
        return index == null
                || Integer.valueOf(1).equals(index.getDeleted())
                || !document.contentHash().equals(index.getContentHash());
    }

    private boolean callPythonUpsert(List<MemoryDocument> documents) {
        try {
            List<Map<String, Object>> items = documents.stream()
                    .map(this::toPythonItem)
                    .toList();
            Map<String, Object> payload = Map.of("items", items);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(aiServiceBaseUrl + "/internal/ai/memories/upsert"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new ApiException("AI memory service unavailable");
            }
            JsonNode root = objectMapper.readTree(response.body());
            return root.path("storeEnabled").asBoolean(false);
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException("AI memory sync failed");
        }
    }

    private List<Map<String, Object>> callPythonSearch(Long spaceId, String query) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("spaceId", spaceId);
            payload.put("query", query);
            payload.put("topK", PLAN_MEMORY_TOP_K);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(aiServiceBaseUrl + "/internal/ai/memories/search"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new ApiException("AI memory search service unavailable");
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode results = root.path("results");
            if (!results.isArray()) {
                return List.of();
            }
            List<Map<String, Object>> memories = new ArrayList<>();
            for (JsonNode result : results) {
                Map<String, Object> memory = new LinkedHashMap<>();
                memory.put("memoryId", result.path("memoryId").asText(""));
                memory.put("sourceType", result.path("sourceType").asText(""));
                memory.put("sourceId", result.path("sourceId").asLong(0));
                memory.put("cityName", result.path("cityName").asText(""));
                memory.put("content", result.path("content").asText(""));
                memory.put("score", result.path("score").asDouble(0));
                memories.add(memory);
            }
            return memories;
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException("AI memory search failed");
        }
    }

    private void enrichMemoryReasons(AiPlanDayGenerateRequest request, List<Map<String, Object>> memories) {
        for (Map<String, Object> memory : memories) {
            memory.put("reason", memoryReasoner.explain(request, memory));
        }
    }

    private Map<String, Object> toPythonItem(MemoryDocument document) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("memoryId", document.memoryId());
        item.put("spaceId", document.spaceId());
        item.put("userId", document.userId());
        item.put("sourceType", document.sourceType());
        item.put("sourceId", document.sourceId());
        item.put("cityCode", document.cityCode());
        item.put("cityName", document.cityName());
        item.put("content", document.content());
        return item;
    }

    private void upsertIndex(MemoryDocument document) {
        AiTravelMemoryIndex index = findIndex(document.memoryId());
        if (index == null) {
            index = new AiTravelMemoryIndex();
            index.setMemoryId(document.memoryId());
        }
        index.setSpaceId(document.spaceId());
        index.setUserId(document.userId());
        index.setSourceType(document.sourceType());
        index.setSourceId(document.sourceId());
        index.setCityCode(document.cityCode());
        index.setCityName(document.cityName());
        index.setContentHash(document.contentHash());
        index.setIndexedAt(LocalDateTime.now());
        index.setDeleted(0);
        if (index.getId() == null) {
            memoryIndexMapper.insert(index);
        } else {
            memoryIndexMapper.updateById(index);
        }
    }

    private AiTravelMemoryIndex findIndex(String memoryId) {
        return memoryIndexMapper.selectOne(new LambdaQueryWrapper<AiTravelMemoryIndex>()
                .eq(AiTravelMemoryIndex::getMemoryId, memoryId)
                .last("LIMIT 1"));
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public record SyncResult(int changedCount, int skippedCount, boolean success) {
    }

    public record MemorySearchResult(boolean success, List<Map<String, Object>> memories, String errorMessage, String query) {
    }
}
