package com.lovetravel.server.modules.ai.service;

import com.lovetravel.server.modules.plan.domain.TravelPlanDay;
import com.lovetravel.server.modules.travel.domain.Trip;
import com.lovetravel.server.modules.travel.domain.TripPost;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class AiTravelMemoryDocumentFactory {

    public Optional<MemoryDocument> fromPlanDay(TravelPlanDay day) {
        String title = normalize(day.getTitle());
        String detail = normalize(day.getDetail());
        if (title.isBlank() && detail.isBlank()) {
            return Optional.empty();
        }

        StringBuilder content = new StringBuilder();
        content.append("Travel plan");
        if (day.getPlanDate() != null) {
            content.append(" on ").append(day.getPlanDate());
        }
        if (!title.isBlank()) {
            content.append("\nTitle: ").append(title);
        }
        if (!detail.isBlank()) {
            content.append("\nDetail: ").append(detail);
        }

        String text = content.toString();
        Long userId = day.getUpdatedByUserId() == null ? day.getCreatedByUserId() : day.getUpdatedByUserId();
        return Optional.of(new MemoryDocument(
                "plan_day_" + day.getId(),
                day.getSpaceId(),
                userId,
                "PLAN_DAY",
                day.getId(),
                null,
                null,
                text,
                sha256(text)));
    }

    public MemoryDocument fromTripPost(TripPost post, Trip trip) {
        String content = normalize(post.getPolishedContent());
        if (content.isBlank()) {
            content = normalize(post.getContent());
        }

        String cityCode = trip == null ? null : trip.getCityCode();
        String cityName = trip == null ? normalize(post.getLocationName()) : normalize(trip.getCityName());

        StringBuilder text = new StringBuilder();
        text.append("Travel diary");
        if (!cityName.isBlank()) {
            text.append(" in ").append(cityName);
        }
        if (post.getPostDate() != null) {
            text.append(" on ").append(post.getPostDate());
        }
        if (!content.isBlank()) {
            text.append("\nContent: ").append(content);
        }

        String memoryContent = text.toString();
        return new MemoryDocument(
                "trip_post_" + post.getId(),
                post.getSpaceId(),
                post.getAuthorUserId(),
                "TRIP_POST",
                post.getId(),
                cityCode,
                cityName.isBlank() ? null : cityName,
                memoryContent,
                sha256(memoryContent));
    }

    public String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to calculate content hash", exception);
        }
    }

    public String buildPlanSearchQuery(String destination, List<String> places, List<String> mustVisitPlaces, String hotelLocation, String notes) {
        StringBuilder query = new StringBuilder();
        appendPart(query, "Intent", "检索历史旅行偏好、日记体验和计划经验，用来辅助当前这一天的旅行规划");
        appendPart(query, "Destination", normalize(destination));
        appendPart(query, "Places", String.join(", ", places == null ? List.of() : places));
        appendPart(query, "Must visit", String.join(", ", mustVisitPlaces == null ? List.of() : mustVisitPlaces));
        appendPart(query, "Hotel", normalize(hotelLocation));
        appendPart(query, "Notes", normalize(notes));
        return query.toString().trim();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private void appendPart(StringBuilder builder, String label, String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append("\n");
        }
        builder.append(label).append(": ").append(normalized);
    }

    public record MemoryDocument(
            String memoryId,
            Long spaceId,
            Long userId,
            String sourceType,
            Long sourceId,
            String cityCode,
            String cityName,
            String content,
            String contentHash) {
    }
}
