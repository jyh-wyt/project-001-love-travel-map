package com.lovetravel.server.modules.travel.controller;

import com.lovetravel.server.modules.auth.service.AuthSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;
import com.lovetravel.server.modules.travel.dto.CreatePostRequest;
import com.lovetravel.server.modules.travel.vo.PostResponse;
import com.lovetravel.server.modules.travel.dto.SetCoverImageRequest;
import com.lovetravel.server.modules.travel.vo.TravelPageResponse;
import com.lovetravel.server.modules.travel.service.TravelService;
import com.lovetravel.server.modules.travel.dto.UpdateDateRangeRequest;
import com.lovetravel.server.modules.travel.dto.UpdatePostRequest;
import com.lovetravel.server.modules.travel.vo.VisitedRegionResponse;

@RestController
@RequestMapping("/api")
public class TravelController {

    private final TravelService travelService;
    private final AuthSessionService authSessionService;

    public TravelController(TravelService travelService, AuthSessionService authSessionService) {
        this.travelService = travelService;
        this.authSessionService = authSessionService;
    }

    @GetMapping("/map/visited-regions")
    public List<VisitedRegionResponse> listVisitedRegions(
            HttpServletRequest servletRequest,
            @RequestParam(value = "provinceCode", required = false) String provinceCode) {
        Long userId = authSessionService.requireCurrentUserId(servletRequest);
        return travelService.listVisitedRegions(userId, provinceCode);
    }

    @GetMapping("/regions/{cityCode}/travel")
    public TravelPageResponse getCityTravel(
            @PathVariable("cityCode") String cityCode,
            HttpServletRequest servletRequest,
            @RequestParam("provinceCode") String provinceCode,
            @RequestParam("provinceName") String provinceName,
            @RequestParam("cityName") String cityName) {
        Long userId = authSessionService.requireCurrentUserId(servletRequest);
        return travelService.getOrCreateCityTravel(userId, cityCode, provinceCode, provinceName, cityName);
    }

    @PutMapping("/trips/{tripId}/date-range")
    public TravelPageResponse updateDateRange(
            @PathVariable("tripId") Long tripId,
            HttpServletRequest servletRequest,
            @Valid @RequestBody UpdateDateRangeRequest request) {
        request.setUserId(authSessionService.requireCurrentUserId(servletRequest));
        return travelService.updateDateRange(tripId, request);
    }

    @PostMapping("/trips/{tripId}/posts")
    public PostResponse createPost(
            @PathVariable("tripId") Long tripId,
            HttpServletRequest servletRequest,
            @Valid @RequestBody CreatePostRequest request) {
        request.setUserId(authSessionService.requireCurrentUserId(servletRequest));
        return travelService.createPost(tripId, request);
    }

    @PutMapping("/posts/{postId}")
    public PostResponse updatePost(
            @PathVariable("postId") Long postId,
            HttpServletRequest servletRequest,
            @Valid @RequestBody UpdatePostRequest request) {
        request.setUserId(authSessionService.requireCurrentUserId(servletRequest));
        return travelService.updatePost(postId, request);
    }

    @PutMapping("/trips/{tripId}/cover-image")
    public TravelPageResponse setCoverImage(
            @PathVariable("tripId") Long tripId,
            HttpServletRequest servletRequest,
            @Valid @RequestBody SetCoverImageRequest request) {
        request.setUserId(authSessionService.requireCurrentUserId(servletRequest));
        return travelService.setCoverImage(tripId, request);
    }

    @DeleteMapping("/posts/{postId}")
    public Map<String, Boolean> deletePost(
            @PathVariable("postId") Long postId,
            HttpServletRequest servletRequest) {
        Long userId = authSessionService.requireCurrentUserId(servletRequest);
        travelService.deletePost(postId, userId);
        return Map.of("success", true);
    }

    @DeleteMapping("/images/{imageId}")
    public TravelPageResponse deleteImage(
            @PathVariable("imageId") Long imageId,
            HttpServletRequest servletRequest) {
        Long userId = authSessionService.requireCurrentUserId(servletRequest);
        return travelService.deleteImage(imageId, userId);
    }
}
