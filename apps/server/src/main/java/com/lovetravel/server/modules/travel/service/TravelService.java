package com.lovetravel.server.modules.travel.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lovetravel.server.common.ApiException;
import com.lovetravel.server.modules.auth.mapper.AppUserMapper;
import com.lovetravel.server.modules.space.mapper.CoupleSpaceMapper;
import com.lovetravel.server.modules.space.mapper.CoupleSpaceMemberMapper;
import com.lovetravel.server.modules.travel.mapper.TripImageMapper;
import com.lovetravel.server.modules.travel.mapper.TripMapper;
import com.lovetravel.server.modules.travel.mapper.TripPostMapper;
import com.lovetravel.server.modules.auth.domain.AppUser;
import com.lovetravel.server.modules.space.domain.CoupleSpace;
import com.lovetravel.server.modules.space.domain.CoupleSpaceMember;
import com.lovetravel.server.modules.travel.domain.Trip;
import com.lovetravel.server.modules.travel.domain.TripImage;
import com.lovetravel.server.modules.travel.domain.TripPost;
import com.lovetravel.server.modules.oss.service.OssService;
import com.lovetravel.server.modules.space.service.SpaceService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.lovetravel.server.modules.travel.dto.CreatePostRequest;
import com.lovetravel.server.modules.travel.dto.ImageRequest;
import com.lovetravel.server.modules.travel.vo.ImageResponse;
import com.lovetravel.server.modules.travel.vo.PostResponse;
import com.lovetravel.server.modules.travel.dto.SetCoverImageRequest;
import com.lovetravel.server.modules.travel.vo.TravelPageResponse;
import com.lovetravel.server.modules.travel.dto.UpdateDateRangeRequest;
import com.lovetravel.server.modules.travel.dto.UpdatePostRequest;
import com.lovetravel.server.modules.travel.vo.VisitedRegionResponse;

@Service
public class TravelService {

    private static final int MAX_IMAGES_PER_POST = 6;

    private final AppUserMapper appUserMapper;
    private final CoupleSpaceMapper coupleSpaceMapper;
    private final CoupleSpaceMemberMapper memberMapper;
    private final TripMapper tripMapper;
    private final TripPostMapper postMapper;
    private final TripImageMapper imageMapper;
    private final OssService ossService;
    private final SpaceService spaceService;

    public TravelService(
            AppUserMapper appUserMapper,
            CoupleSpaceMapper coupleSpaceMapper,
            CoupleSpaceMemberMapper memberMapper,
            TripMapper tripMapper,
            TripPostMapper postMapper,
            TripImageMapper imageMapper,
            OssService ossService,
            SpaceService spaceService) {
        this.appUserMapper = appUserMapper;
        this.coupleSpaceMapper = coupleSpaceMapper;
        this.memberMapper = memberMapper;
        this.tripMapper = tripMapper;
        this.postMapper = postMapper;
        this.imageMapper = imageMapper;
        this.ossService = ossService;
        this.spaceService = spaceService;
    }

    public List<VisitedRegionResponse> listVisitedRegions(Long userId, String provinceCode) {
        CoupleSpace space = spaceService.requireActiveSpace(userId);
        LambdaQueryWrapper<Trip> query = new LambdaQueryWrapper<Trip>()
                .eq(Trip::getSpaceId, space.getId())
                .eq(Trip::getDeleted, 0)
                .orderByDesc(Trip::getUpdatedAt);
        if (provinceCode != null && !provinceCode.isBlank()) {
            query.eq(Trip::getProvinceCode, provinceCode);
        }

        List<VisitedRegionResponse> responses = new ArrayList<>();
        for (Trip trip : tripMapper.selectList(query)) {
            Long recordCount = postMapper.selectCount(new LambdaQueryWrapper<TripPost>()
                    .eq(TripPost::getTripId, trip.getId())
                    .eq(TripPost::getDeleted, 0));
            if (recordCount == 0 && trip.getStartDate() == null && trip.getEndDate() == null) {
                continue;
            }
            responses.add(new VisitedRegionResponse(
                    trip.getId(),
                    trip.getProvinceCode(),
                    trip.getProvinceName(),
                    trip.getCityCode(),
                    trip.getCityName(),
                    resolveDisplayImageUrl(resolveCoverImageUrl(trip)),
                    trip.getStartDate(),
                    trip.getEndDate(),
                    recordCount,
                    trip.getUpdatedAt()));
        }
        return responses;
    }

    @Transactional
    public TravelPageResponse getOrCreateCityTravel(
            Long userId,
            String cityCode,
            String provinceCode,
            String provinceName,
            String cityName) {
        CoupleSpace space = spaceService.requireActiveSpace(userId);
        requireText(cityCode, "城市编码不能为空");
        requireText(provinceCode, "省份编码不能为空");
        requireText(provinceName, "省份名称不能为空");
        requireText(cityName, "城市名称不能为空");

        Trip trip = findTrip(space.getId(), cityCode);
        if (trip != null && !isSameCityName(trip.getCityName(), cityName)) {
            Trip sameNameTrip = findTripByCityName(space.getId(), provinceCode, cityName);
            if (sameNameTrip != null) {
                trip = correctTripCity(sameNameTrip, cityCode, provinceCode, provinceName, cityName);
            }
        }
        if (trip == null) {
            Trip sameNameTrip = findTripByCityName(space.getId(), provinceCode, cityName);
            if (sameNameTrip != null) {
                trip = correctTripCity(sameNameTrip, cityCode, provinceCode, provinceName, cityName);
            }
        }
        if (trip == null) {
            trip = new Trip();
            trip.setSpaceId(space.getId());
            trip.setProvinceCode(provinceCode);
            trip.setProvinceName(provinceName);
            trip.setCityCode(cityCode);
            trip.setCityName(cityName);
            trip.setTitle(cityName + "旅行记录");
            trip.setCreatedByUserId(userId);
            trip.setDeleted(0);
            tripMapper.insert(trip);
        }

        return toTravelPageResponse(trip);
    }

    @Transactional
    public TravelPageResponse updateDateRange(Long tripId, UpdateDateRangeRequest request) {
        spaceService.requireEditableActiveSpace(request.getUserId());
        Trip trip = requireTripForUser(tripId, request.getUserId());
        validateDateRange(request.getStartDate(), request.getEndDate());
        trip.setStartDate(request.getStartDate());
        trip.setEndDate(request.getEndDate());
        tripMapper.updateById(trip);
        return toTravelPageResponse(tripMapper.selectById(tripId));
    }

    @Transactional
    public PostResponse createPost(Long tripId, CreatePostRequest request) {
        spaceService.requireEditableActiveSpace(request.getUserId());
        Trip trip = requireTripForUser(tripId, request.getUserId());

        List<ImageRequest> imageRequests = request.getImages() == null ? List.of() : request.getImages();
        if (imageRequests.size() > MAX_IMAGES_PER_POST) {
            throw new ApiException("一条旅行记录最多上传 6 张图片");
        }
        if ((request.getContent() == null || request.getContent().isBlank()) && imageRequests.isEmpty()) {
            throw new ApiException("日记文字和图片至少填写一项");
        }

        TripPost post = new TripPost();
        post.setSpaceId(trip.getSpaceId());
        post.setTripId(trip.getId());
        post.setAuthorUserId(request.getUserId());
        post.setContent(request.getContent());
        post.setLocationName(request.getLocationName());
        post.setDeleted(0);
        postMapper.insert(post);

        List<ImageResponse> images = new ArrayList<>();
        int index = 0;
        for (ImageRequest imageRequest : imageRequests) {
            TripImage image = new TripImage();
            image.setSpaceId(trip.getSpaceId());
            image.setTripId(trip.getId());
            image.setPostId(post.getId());
            image.setUploaderUserId(request.getUserId());
            image.setOssObjectKey(imageRequest.getOssObjectKey());
            image.setImageUrl(resolveStoredImageUrl(imageRequest));
            image.setSortOrder(imageRequest.getSortOrder() == null ? index : imageRequest.getSortOrder());
            image.setDeleted(0);
            imageMapper.insert(image);
            images.add(toImageResponse(image));
            index++;
        }

        if ((trip.getCoverImageUrl() == null || trip.getCoverImageUrl().isBlank()) && !images.isEmpty()) {
            refreshCoverImage(trip);
        }

        return toPostResponse(post, images);
    }

    @Transactional
    public PostResponse updatePost(Long postId, UpdatePostRequest request) {
        spaceService.requireEditableActiveSpace(request.getUserId());
        TripPost post = postMapper.selectById(postId);
        if (post == null || Integer.valueOf(1).equals(post.getDeleted())) {
            throw new ApiException("旅行记录不存在或已删除");
        }

        Trip trip = requireTripForUser(post.getTripId(), request.getUserId());
        List<ImageRequest> newImageRequests = request.getImages() == null ? List.of() : request.getImages();
        long currentImageCount = imageMapper.selectCount(new LambdaQueryWrapper<TripImage>()
                .eq(TripImage::getPostId, postId)
                .eq(TripImage::getDeleted, 0));
        if (currentImageCount + newImageRequests.size() > MAX_IMAGES_PER_POST) {
            throw new ApiException("一条旅行记录最多保留 6 张图片");
        }
        if ((request.getContent() == null || request.getContent().isBlank()) && currentImageCount == 0 && newImageRequests.isEmpty()) {
            throw new ApiException("日记文字和图片至少保留一项");
        }

        post.setContent(request.getContent());
        post.setLocationName(request.getLocationName());
        post.setUpdatedAt(LocalDateTime.now());
        postMapper.updateById(post);

        int sortOrder = nextImageSortOrder(postId);
        for (ImageRequest imageRequest : newImageRequests) {
            TripImage image = new TripImage();
            image.setSpaceId(trip.getSpaceId());
            image.setTripId(trip.getId());
            image.setPostId(post.getId());
            image.setUploaderUserId(request.getUserId());
            image.setOssObjectKey(imageRequest.getOssObjectKey());
            image.setImageUrl(resolveStoredImageUrl(imageRequest));
            image.setSortOrder(imageRequest.getSortOrder() == null ? sortOrder : imageRequest.getSortOrder());
            image.setDeleted(0);
            imageMapper.insert(image);
            sortOrder++;
        }

        refreshCoverImage(trip);
        return toPostResponse(postMapper.selectById(postId), listImagesForPost(postId, trip.getSpaceId()));
    }

    @Transactional
    public TravelPageResponse setCoverImage(Long tripId, SetCoverImageRequest request) {
        spaceService.requireEditableActiveSpace(request.getUserId());
        Trip trip = requireTripForUser(tripId, request.getUserId());
        TripImage image = imageMapper.selectById(request.getImageId());
        if (image == null || Integer.valueOf(1).equals(image.getDeleted())) {
            throw new ApiException("图片不存在");
        }
        if (!trip.getSpaceId().equals(image.getSpaceId()) || !trip.getId().equals(image.getTripId())) {
            throw new ApiException("只能将当前城市记录里的图片设为封面");
        }

        trip.setCoverImageUrl(image.getOssObjectKey());
        tripMapper.updateById(trip);
        return toTravelPageResponse(tripMapper.selectById(tripId));
    }

    @Transactional
    public void deletePost(Long postId, Long userId) {
        spaceService.requireEditableActiveSpace(userId);
        TripPost post = postMapper.selectById(postId);
        if (post == null || Integer.valueOf(1).equals(post.getDeleted())) {
            throw new ApiException("旅行记录不存在或已删除");
        }

        Trip trip = requireTripForUser(post.getTripId(), userId);
        post.setDeleted(1);
        postMapper.updateById(post);

        List<TripImage> images = imageMapper.selectList(new LambdaQueryWrapper<TripImage>()
                .eq(TripImage::getPostId, postId)
                .eq(TripImage::getDeleted, 0));
        for (TripImage image : images) {
            ossService.deleteObjectIfPresent(image.getOssObjectKey());
            image.setDeleted(1);
            imageMapper.updateById(image);
        }

        refreshCoverImage(trip);
    }

    @Transactional
    public TravelPageResponse deleteImage(Long imageId, Long userId) {
        spaceService.requireEditableActiveSpace(userId);
        TripImage image = imageMapper.selectById(imageId);
        if (image == null || Integer.valueOf(1).equals(image.getDeleted())) {
            throw new ApiException("图片不存在或已删除");
        }

        Trip trip = requireTripForUser(image.getTripId(), userId);
        ossService.deleteObjectIfPresent(image.getOssObjectKey());
        image.setDeleted(1);
        imageMapper.updateById(image);
        refreshCoverImage(trip);
        return toTravelPageResponse(tripMapper.selectById(trip.getId()));
    }

    private TravelPageResponse toTravelPageResponse(Trip trip) {
        List<TripPost> posts = postMapper.selectList(new LambdaQueryWrapper<TripPost>()
                .eq(TripPost::getTripId, trip.getId())
                .eq(TripPost::getDeleted, 0)
                .orderByDesc(TripPost::getCreatedAt));
        List<PostResponse> postResponses = new ArrayList<>();
        for (TripPost post : posts) {
            List<ImageResponse> images = imageMapper.selectList(new LambdaQueryWrapper<TripImage>()
                            .eq(TripImage::getPostId, post.getId())
                            .eq(TripImage::getDeleted, 0))
                    .stream()
                    .sorted(Comparator.comparing(TripImage::getSortOrder, Comparator.nullsLast(Integer::compareTo)))
                    .map(this::toImageResponse)
                    .toList();
            postResponses.add(toPostResponse(post, images));
        }

        return new TravelPageResponse(
                trip.getId(),
                trip.getProvinceCode(),
                trip.getProvinceName(),
                trip.getCityCode(),
                trip.getCityName(),
                trip.getTitle(),
                resolveDisplayImageUrl(resolveCoverImageUrl(trip)),
                trip.getStartDate(),
                trip.getEndDate(),
                postResponses);
    }

    private PostResponse toPostResponse(TripPost post, List<ImageResponse> images) {
        return new PostResponse(
                post.getId(),
                post.getAuthorUserId(),
                resolveAuthorNickname(post.getAuthorUserId()),
                post.getContent(),
                post.getPolishedContent(),
                post.getLocationName(),
                post.getCreatedAt(),
                images);
    }

    private String resolveAuthorNickname(Long authorUserId) {
        if (authorUserId == null) {
            return "未知用户";
        }
        AppUser author = appUserMapper.selectById(authorUserId);
        if (author == null || author.getNickname() == null || author.getNickname().isBlank()) {
            return "用户" + authorUserId;
        }
        return author.getNickname();
    }

    private List<ImageResponse> listImagesForPost(Long postId, Long spaceId) {
        return imageMapper.selectList(new LambdaQueryWrapper<TripImage>()
                        .eq(TripImage::getPostId, postId)
                        .eq(TripImage::getSpaceId, spaceId)
                        .eq(TripImage::getDeleted, 0))
                .stream()
                .sorted(Comparator.comparing(TripImage::getSortOrder, Comparator.nullsLast(Integer::compareTo)))
                .map(this::toImageResponse)
                .toList();
    }

    private int nextImageSortOrder(Long postId) {
        return imageMapper.selectList(new LambdaQueryWrapper<TripImage>()
                        .eq(TripImage::getPostId, postId)
                        .eq(TripImage::getDeleted, 0))
                .stream()
                .map(TripImage::getSortOrder)
                .filter(value -> value != null)
                .max(Comparator.naturalOrder())
                .orElse(-1) + 1;
    }

    private ImageResponse toImageResponse(TripImage image) {
        return new ImageResponse(
                image.getId(),
                resolveDisplayImageUrl(resolveImageReference(image)),
                image.getOssObjectKey(),
                image.getSortOrder());
    }

    private String resolveCoverImageUrl(Trip trip) {
        if (trip.getCoverImageUrl() != null && !trip.getCoverImageUrl().isBlank()) {
            return trip.getCoverImageUrl();
        }
        TripImage firstImage = imageMapper.selectOne(new LambdaQueryWrapper<TripImage>()
                .eq(TripImage::getTripId, trip.getId())
                .eq(TripImage::getDeleted, 0)
                .orderByAsc(TripImage::getCreatedAt)
                .last("LIMIT 1"));
        return firstImage == null ? null : resolveImageReference(firstImage);
    }

    private void refreshCoverImage(Trip trip) {
        TripImage firstImage = imageMapper.selectOne(new LambdaQueryWrapper<TripImage>()
                .eq(TripImage::getTripId, trip.getId())
                .eq(TripImage::getDeleted, 0)
                .orderByAsc(TripImage::getCreatedAt)
                .last("LIMIT 1"));
        trip.setCoverImageUrl(firstImage == null ? null : resolveImageReference(firstImage));
        tripMapper.updateById(trip);
    }

    private String resolveImageReference(TripImage image) {
        if (image.getOssObjectKey() != null && !image.getOssObjectKey().isBlank()) {
            return image.getOssObjectKey();
        }
        return image.getImageUrl();
    }

    private String resolveStoredImageUrl(ImageRequest imageRequest) {
        if (imageRequest.getOssObjectKey() != null && !imageRequest.getOssObjectKey().isBlank()) {
            return imageRequest.getOssObjectKey();
        }
        return imageRequest.getImageUrl();
    }

    private String resolveDisplayImageUrl(String imageReference) {
        if (imageReference == null || imageReference.isBlank()) {
            return null;
        }
        if (imageReference.startsWith("http://") || imageReference.startsWith("https://")) {
            return imageReference;
        }
        return ossService.resolveDisplayUrl(imageReference);
    }

    private Trip requireTripForUser(Long tripId, Long userId) {
        CoupleSpace space = spaceService.requireActiveSpace(userId);
        Trip trip = tripMapper.selectById(tripId);
        if (trip == null || Integer.valueOf(1).equals(trip.getDeleted())) {
            throw new ApiException("旅行记录不存在");
        }
        if (!space.getId().equals(trip.getSpaceId())) {
            throw new ApiException("当前用户无权访问该旅行记录");
        }
        return trip;
    }

    private Trip findTrip(Long spaceId, String cityCode) {
        return tripMapper.selectOne(new LambdaQueryWrapper<Trip>()
                .eq(Trip::getSpaceId, spaceId)
                .eq(Trip::getCityCode, cityCode)
                .eq(Trip::getDeleted, 0)
                .last("LIMIT 1"));
    }

    private Trip findTripByCityName(Long spaceId, String provinceCode, String cityName) {
        String normalizedCityName = normalizeCityName(cityName);
        for (Trip trip : tripMapper.selectList(new LambdaQueryWrapper<Trip>()
                .eq(Trip::getSpaceId, spaceId)
                .eq(Trip::getProvinceCode, provinceCode)
                .eq(Trip::getDeleted, 0))) {
            if (isSameCityName(trip.getCityName(), normalizedCityName)) {
                return trip;
            }
        }
        return null;
    }

    private Trip correctTripCity(
            Trip trip,
            String cityCode,
            String provinceCode,
            String provinceName,
            String cityName) {
        trip.setCityCode(cityCode);
        trip.setCityName(cityName);
        trip.setProvinceCode(provinceCode);
        trip.setProvinceName(provinceName);
        trip.setTitle(cityName + "旅行记录");
        tripMapper.updateById(trip);
        return trip;
    }

    private CoupleSpaceMember ensurePersonalOrSharedSpace(Long userId) {
        requireUser(userId);
        CoupleSpaceMember member = memberMapper.selectOne(new LambdaQueryWrapper<CoupleSpaceMember>()
                .eq(CoupleSpaceMember::getUserId, userId)
                .eq(CoupleSpaceMember::getDeleted, 0)
                .last("LIMIT 1"));
        if (member != null) {
            return member;
        }

        CoupleSpace space = new CoupleSpace();
        space.setSpaceName("我的旅行地图");
        space.setCreatorUserId(userId);
        space.setMemberLimit(2);
        space.setDeleted(0);
        coupleSpaceMapper.insert(space);

        CoupleSpaceMember newMember = new CoupleSpaceMember();
        newMember.setSpaceId(space.getId());
        newMember.setUserId(userId);
        newMember.setRole("creator");
        newMember.setDeleted(0);
        memberMapper.insert(newMember);
        return newMember;
    }

    private void requireUser(Long userId) {
        AppUser user = appUserMapper.selectById(userId);
        if (user == null || Integer.valueOf(1).equals(user.getDeleted())) {
            throw new ApiException("用户不存在");
        }
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new ApiException("结束日期不能早于开始日期");
        }
    }

    private void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ApiException(message);
        }
    }

    private boolean isSameCityName(String left, String right) {
        return normalizeCityName(left).equals(normalizeCityName(right));
    }

    private String normalizeCityName(String name) {
        if (name == null) {
            return "";
        }
        return name.replaceAll("[市区县盟州地区]$", "");
    }
}
