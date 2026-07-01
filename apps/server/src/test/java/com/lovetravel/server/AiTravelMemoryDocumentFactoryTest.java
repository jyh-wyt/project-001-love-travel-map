package com.lovetravel.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lovetravel.server.modules.ai.service.AiTravelMemoryDocumentFactory;
import com.lovetravel.server.modules.ai.service.AiTravelMemoryDocumentFactory.MemoryDocument;
import com.lovetravel.server.modules.plan.domain.TravelPlanDay;
import com.lovetravel.server.modules.travel.domain.Trip;
import com.lovetravel.server.modules.travel.domain.TripPost;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiTravelMemoryDocumentFactoryTest {

    private final AiTravelMemoryDocumentFactory factory = new AiTravelMemoryDocumentFactory();

    @Test
    void buildsStableTripPostMemoryDocument() {
        Trip trip = new Trip();
        trip.setCityCode("370200");
        trip.setCityName("Qingdao");

        TripPost post = new TripPost();
        post.setId(12L);
        post.setSpaceId(7L);
        post.setAuthorUserId(5L);
        post.setPostDate(LocalDate.of(2026, 6, 19));
        post.setContent(" walked by the sea ");

        MemoryDocument document = factory.fromTripPost(post, trip);

        assertEquals("trip_post_12", document.memoryId());
        assertEquals("TRIP_POST", document.sourceType());
        assertEquals("370200", document.cityCode());
        assertEquals("Qingdao", document.cityName());
        assertTrue(document.content().contains("walked by the sea"));
        assertEquals(document.contentHash(), factory.sha256(document.content()));
    }

    @Test
    void skipsBlankPlanDayContent() {
        TravelPlanDay day = new TravelPlanDay();
        day.setId(8L);
        day.setSpaceId(7L);
        day.setUpdatedByUserId(5L);
        day.setTitle("  ");
        day.setDetail("");

        assertTrue(factory.fromPlanDay(day).isEmpty());
    }

    @Test
    void buildsSearchQueryFromPlanRequestParts() {
        String query = factory.buildPlanSearchQuery(
                "Qingdao",
                List.of("Badaguan", "Xiaomai Island"),
                List.of("Xiaomai Island"),
                "Prefer relaxed photo spots");

        assertTrue(query.contains("Qingdao"));
        assertTrue(query.contains("Badaguan"));
        assertTrue(query.contains("Xiaomai Island"));
        assertTrue(query.contains("Prefer relaxed photo spots"));
    }
}
