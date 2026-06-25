package com.lovetravel.server.modules.space.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lovetravel.server.common.ApiException;
import com.lovetravel.server.modules.auth.mapper.AppUserMapper;
import com.lovetravel.server.modules.space.mapper.CoupleSpaceMapper;
import com.lovetravel.server.modules.space.mapper.CoupleSpaceMemberMapper;
import com.lovetravel.server.modules.space.mapper.InviteCodeMapper;
import com.lovetravel.server.modules.plan.mapper.TravelPlanDayMapper;
import com.lovetravel.server.modules.travel.mapper.TripImageMapper;
import com.lovetravel.server.modules.travel.mapper.TripMapper;
import com.lovetravel.server.modules.travel.mapper.TripPostMapper;
import com.lovetravel.server.modules.auth.domain.AppUser;
import com.lovetravel.server.modules.space.domain.CoupleSpace;
import com.lovetravel.server.modules.space.domain.CoupleSpaceMember;
import com.lovetravel.server.modules.space.domain.InviteCode;
import com.lovetravel.server.modules.plan.domain.TravelPlanDay;
import com.lovetravel.server.modules.travel.domain.Trip;
import com.lovetravel.server.modules.travel.domain.TripImage;
import com.lovetravel.server.modules.travel.domain.TripPost;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.lovetravel.server.modules.space.vo.CoupleSpaceResponse;
import com.lovetravel.server.modules.space.dto.CreateInviteCodeRequest;
import com.lovetravel.server.modules.space.dto.CreateSpaceRequest;
import com.lovetravel.server.modules.space.vo.InviteCodeResponse;
import com.lovetravel.server.modules.space.dto.JoinSpaceRequest;
import com.lovetravel.server.modules.space.dto.UnbindSpaceRequest;

@Service
public class SpaceService {

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String SPACE_TYPE_PERSONAL = "PERSONAL";
    private static final String SPACE_TYPE_COUPLE = "COUPLE";
    private static final String SPACE_STATUS_ACTIVE = "ACTIVE";
    private static final String SPACE_STATUS_WAITING = "WAITING";
    private static final String WAITING_COUPLE_MESSAGE = "未邀请你的伴侣不可使用。";
    private static final String INVITE_CODE_KEY_PREFIX = "love-travel:invite-code:";
    private static final Duration INVITE_CODE_RESERVE_TTL = Duration.ofSeconds(65);

    private final AppUserMapper appUserMapper;
    private final CoupleSpaceMapper coupleSpaceMapper;
    private final CoupleSpaceMemberMapper memberMapper;
    private final InviteCodeMapper inviteCodeMapper;
    private final TravelPlanDayMapper planDayMapper;
    private final TripMapper tripMapper;
    private final TripPostMapper postMapper;
    private final TripImageMapper imageMapper;
    private final StringRedisTemplate redisTemplate;

    public SpaceService(
            AppUserMapper appUserMapper,
            CoupleSpaceMapper coupleSpaceMapper,
            CoupleSpaceMemberMapper memberMapper,
            InviteCodeMapper inviteCodeMapper,
            TravelPlanDayMapper planDayMapper,
            TripMapper tripMapper,
            TripPostMapper postMapper,
            TripImageMapper imageMapper,
            StringRedisTemplate redisTemplate) {
        this.appUserMapper = appUserMapper;
        this.coupleSpaceMapper = coupleSpaceMapper;
        this.memberMapper = memberMapper;
        this.inviteCodeMapper = inviteCodeMapper;
        this.planDayMapper = planDayMapper;
        this.tripMapper = tripMapper;
        this.postMapper = postMapper;
        this.imageMapper = imageMapper;
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    public CoupleSpaceResponse createSpace(CreateSpaceRequest request) {
        return createCoupleSpace(request.getUserId());
    }

    @Transactional
    public CoupleSpaceResponse getCurrentSpace(Long userId) {
        return ensureActiveSpace(userId);
    }

    @Transactional
    public CoupleSpaceResponse ensureActiveSpace(Long userId) {
        AppUser user = requireUser(userId);
        CoupleSpace activeSpace = findActiveMembershipSpace(user);
        if (activeSpace != null) {
            normalizeLegacySpace(activeSpace);
            ensurePersonalSpaceExists(user);
            return toResponse(activeSpace, user);
        }

        CoupleSpaceMember membership = findMembership(userId);
        if (membership != null) {
            CoupleSpace space = requireSpace(membership.getSpaceId());
            normalizeLegacySpace(space);
            user.setActiveSpaceId(space.getId());
            appUserMapper.updateById(user);
            return toResponse(space, user);
        }

        CoupleSpace personalSpace = createPersonalSpaceForUser(user, true);
        return toResponse(personalSpace, user);
    }

    public List<CoupleSpaceResponse> listSpaces(Long userId) {
        ensureActiveSpace(userId);
        AppUser user = requireUser(userId);
        ensurePersonalSpaceExists(user);
        return memberMapper.selectList(new LambdaQueryWrapper<CoupleSpaceMember>()
                        .eq(CoupleSpaceMember::getUserId, userId)
                        .eq(CoupleSpaceMember::getDeleted, 0))
                .stream()
                .map(member -> coupleSpaceMapper.selectById(member.getSpaceId()))
                .filter(space -> space != null && !Integer.valueOf(1).equals(space.getDeleted()))
                .peek(this::normalizeLegacySpace)
                .sorted(Comparator.comparing((CoupleSpace space) -> !space.getId().equals(user.getActiveSpaceId()))
                        .thenComparing(space -> SPACE_TYPE_COUPLE.equals(space.getSpaceType()))
                        .thenComparing(CoupleSpace::getId))
                .map(space -> toResponse(space, user))
                .toList();
    }

    @Transactional
    public CoupleSpaceResponse createCoupleSpace(Long userId) {
        ensureActiveSpace(userId);
        AppUser user = requireUser(userId);
        CoupleSpace existingCoupleSpace = findUserCoupleSpace(userId);
        if (existingCoupleSpace != null) {
            user.setActiveSpaceId(existingCoupleSpace.getId());
            appUserMapper.updateById(user);
            return toResponse(existingCoupleSpace, user);
        }

        CoupleSpace space = new CoupleSpace();
        space.setSpaceName(buildWaitingCoupleSpaceName(user));
        space.setSpaceType(SPACE_TYPE_COUPLE);
        space.setStatus(SPACE_STATUS_WAITING);
        space.setCreatorUserId(userId);
        space.setMemberLimit(2);
        space.setDeleted(0);
        coupleSpaceMapper.insert(space);

        CoupleSpaceMember member = new CoupleSpaceMember();
        member.setSpaceId(space.getId());
        member.setUserId(userId);
        member.setRole("owner");
        member.setDeleted(0);
        memberMapper.insert(member);

        user.setActiveSpaceId(space.getId());
        appUserMapper.updateById(user);
        return toResponse(space, user);
    }

    @Transactional
    public CoupleSpaceResponse activateSpace(Long userId, Long spaceId) {
        AppUser user = requireUser(userId);
        CoupleSpaceMember membership = findMembership(userId, spaceId);
        if (membership == null) {
            throw new ApiException("当前用户不属于该旅行空间");
        }
        CoupleSpace space = requireSpace(spaceId);
        normalizeLegacySpace(space);
        user.setActiveSpaceId(spaceId);
        appUserMapper.updateById(user);
        return toResponse(space, user);
    }

    @Transactional
    public CoupleSpace requireActiveSpace(Long userId) {
        ensureActiveSpace(userId);
        AppUser user = requireUser(userId);
        return requireSpace(user.getActiveSpaceId());
    }

    @Transactional
    public CoupleSpace requireEditableActiveSpace(Long userId) {
        CoupleSpace space = requireActiveSpace(userId);
        normalizeLegacySpace(space);
        if (!isEditable(space)) {
            throw new ApiException(WAITING_COUPLE_MESSAGE);
        }
        return space;
    }

    @Transactional
    public InviteCodeResponse createInviteCode(Long spaceId, CreateInviteCodeRequest request) {
        requireUser(request.getUserId());
        CoupleSpace space = requireSpace(spaceId);
        normalizeLegacySpace(space);
        CoupleSpaceMember membership = findMembership(request.getUserId(), spaceId);
        if (membership == null) {
            throw new ApiException("当前用户不属于该情侣空间");
        }
        if (!SPACE_TYPE_COUPLE.equals(space.getSpaceType())) {
            throw new ApiException("只能为情侣空间生成邀请码");
        }
        if (countMembers(spaceId) >= space.getMemberLimit()) {
            throw new ApiException("情侣空间人数已满");
        }

        InviteCode inviteCode = new InviteCode();
        inviteCode.setSpaceId(spaceId);
        inviteCode.setCode(generateAvailableCode());
        inviteCode.setStatus("unused");
        inviteCode.setExpireAt(LocalDateTime.now().plusMinutes(1));
        inviteCode.setDeleted(0);
        inviteCodeMapper.insert(inviteCode);
        cacheInviteCode(inviteCode);

        return new InviteCodeResponse(inviteCode.getCode(), inviteCode.getExpireAt());
    }

    @Transactional
    public CoupleSpaceResponse joinByInviteCode(JoinSpaceRequest request) {
        AppUser user = requireUser(request.getUserId());
        ensureActiveSpace(request.getUserId());

        String redisInviteCodeId = redisTemplate.opsForValue().get(inviteCodeKey(request.getCode()));
        if (redisInviteCodeId == null) {
            expireInviteCodeIfNeeded(request.getCode());
            throw new ApiException("邀请码不存在或已过期");
        }

        InviteCode inviteCode = inviteCodeMapper.selectOne(new LambdaQueryWrapper<InviteCode>()
                .eq(InviteCode::getCode, request.getCode())
                .eq(InviteCode::getDeleted, 0));
        if (inviteCode == null) {
            throw new ApiException("邀请码不存在");
        }
        if (!"unused".equals(inviteCode.getStatus())) {
            throw new ApiException("邀请码已使用或已失效");
        }
        if (inviteCode.getExpireAt().isBefore(LocalDateTime.now())) {
            inviteCode.setStatus("expired");
            inviteCodeMapper.updateById(inviteCode);
            throw new ApiException("邀请码已过期");
        }

        CoupleSpace targetSpace = requireSpace(inviteCode.getSpaceId());
        normalizeLegacySpace(targetSpace);
        if (!SPACE_TYPE_COUPLE.equals(targetSpace.getSpaceType())) {
            throw new ApiException("只能加入情侣空间");
        }
        if (findMembership(request.getUserId(), targetSpace.getId()) != null) {
            user.setActiveSpaceId(targetSpace.getId());
            appUserMapper.updateById(user);
            return toResponse(targetSpace, user);
        }
        if (findUserCoupleSpace(request.getUserId()) != null) {
            throw new ApiException("当前用户已经加入其他情侣空间");
        }
        if (countMembers(targetSpace.getId()) >= targetSpace.getMemberLimit()) {
            throw new ApiException("情侣空间人数已满");
        }

        CoupleSpaceMember member = new CoupleSpaceMember();
        member.setSpaceId(targetSpace.getId());
        member.setUserId(request.getUserId());
        member.setRole("member");
        member.setDeleted(0);
        memberMapper.insert(member);

        targetSpace.setStatus(SPACE_STATUS_ACTIVE);
        targetSpace.setSpaceName(buildCoupleSpaceName(targetSpace.getCreatorUserId(), request.getUserId()));
        coupleSpaceMapper.updateById(targetSpace);

        user.setActiveSpaceId(targetSpace.getId());
        appUserMapper.updateById(user);

        inviteCode.setStatus("used");
        inviteCode.setUsedByUserId(request.getUserId());
        inviteCode.setUsedAt(LocalDateTime.now());
        inviteCodeMapper.updateById(inviteCode);
        redisTemplate.delete(inviteCodeKey(inviteCode.getCode()));

        return toResponse(targetSpace, user);
    }

    @Transactional
    public CoupleSpaceResponse unbindSpace(UnbindSpaceRequest request) {
        AppUser user = requireUser(request.getUserId());
        CoupleSpaceMember membership = findMembership(request.getUserId());
        if (membership == null) {
            throw new ApiException("当前还没有情侣空间");
        }
        if (countMembers(membership.getSpaceId()) < 2) {
            throw new ApiException("当前还是个人空间，不需要解绑");
        }

        membership.setDeleted(1);
        memberMapper.updateById(membership);

        CoupleSpace personalSpace = createPersonalSpaceForUser(user, true);
        return toResponse(personalSpace, user);
    }

    private void migratePersonalSpace(Long sourceSpaceId, Long targetSpaceId) {
        for (TravelPlanDay day : planDayMapper.selectList(new LambdaQueryWrapper<TravelPlanDay>()
                .eq(TravelPlanDay::getSpaceId, sourceSpaceId)
                .eq(TravelPlanDay::getDeleted, 0))) {
            day.setSpaceId(targetSpaceId);
            planDayMapper.updateById(day);
        }
        for (Trip trip : tripMapper.selectList(new LambdaQueryWrapper<Trip>()
                .eq(Trip::getSpaceId, sourceSpaceId)
                .eq(Trip::getDeleted, 0))) {
            Trip targetTrip = tripMapper.selectOne(new LambdaQueryWrapper<Trip>()
                    .eq(Trip::getSpaceId, targetSpaceId)
                    .eq(Trip::getCityCode, trip.getCityCode())
                    .eq(Trip::getDeleted, 0)
                    .last("LIMIT 1"));
            if (targetTrip == null) {
                trip.setSpaceId(targetSpaceId);
                tripMapper.updateById(trip);
                continue;
            }

            for (TripPost post : postMapper.selectList(new LambdaQueryWrapper<TripPost>()
                    .eq(TripPost::getTripId, trip.getId())
                    .eq(TripPost::getDeleted, 0))) {
                post.setSpaceId(targetSpaceId);
                post.setTripId(targetTrip.getId());
                postMapper.updateById(post);
            }
            for (TripImage image : imageMapper.selectList(new LambdaQueryWrapper<TripImage>()
                    .eq(TripImage::getTripId, trip.getId())
                    .eq(TripImage::getDeleted, 0))) {
                image.setSpaceId(targetSpaceId);
                image.setTripId(targetTrip.getId());
                imageMapper.updateById(image);
            }

            if ((targetTrip.getCoverImageUrl() == null || targetTrip.getCoverImageUrl().isBlank())
                    && trip.getCoverImageUrl() != null
                    && !trip.getCoverImageUrl().isBlank()) {
                targetTrip.setCoverImageUrl(trip.getCoverImageUrl());
                tripMapper.updateById(targetTrip);
            }
            trip.setDeleted(1);
            tripMapper.updateById(trip);
        }
        for (TripPost post : postMapper.selectList(new LambdaQueryWrapper<TripPost>()
                .eq(TripPost::getSpaceId, sourceSpaceId)
                .eq(TripPost::getDeleted, 0))) {
            post.setSpaceId(targetSpaceId);
            postMapper.updateById(post);
        }
        for (TripImage image : imageMapper.selectList(new LambdaQueryWrapper<TripImage>()
                .eq(TripImage::getSpaceId, sourceSpaceId)
                .eq(TripImage::getDeleted, 0))) {
            image.setSpaceId(targetSpaceId);
            imageMapper.updateById(image);
        }
    }

    private AppUser requireUser(Long userId) {
        AppUser user = appUserMapper.selectById(userId);
        if (user == null || Integer.valueOf(1).equals(user.getDeleted())) {
            throw new ApiException("用户不存在");
        }
        return user;
    }

    private CoupleSpace requireSpace(Long spaceId) {
        CoupleSpace space = coupleSpaceMapper.selectById(spaceId);
        if (space == null || Integer.valueOf(1).equals(space.getDeleted())) {
            throw new ApiException("情侣空间不存在");
        }
        return space;
    }

    private CoupleSpaceMember findMembership(Long userId) {
        return memberMapper.selectOne(new LambdaQueryWrapper<CoupleSpaceMember>()
                .eq(CoupleSpaceMember::getUserId, userId)
                .eq(CoupleSpaceMember::getDeleted, 0)
                .last("LIMIT 1"));
    }

    private CoupleSpaceMember findMembership(Long userId, Long spaceId) {
        return memberMapper.selectOne(new LambdaQueryWrapper<CoupleSpaceMember>()
                .eq(CoupleSpaceMember::getUserId, userId)
                .eq(CoupleSpaceMember::getSpaceId, spaceId)
                .eq(CoupleSpaceMember::getDeleted, 0)
                .last("LIMIT 1"));
    }

    private CoupleSpace findActiveMembershipSpace(AppUser user) {
        if (user.getActiveSpaceId() == null) {
            return null;
        }
        CoupleSpaceMember membership = findMembership(user.getId(), user.getActiveSpaceId());
        if (membership == null) {
            return null;
        }
        CoupleSpace space = coupleSpaceMapper.selectById(user.getActiveSpaceId());
        if (space == null || Integer.valueOf(1).equals(space.getDeleted())) {
            return null;
        }
        return space;
    }

    private CoupleSpace findUserCoupleSpace(Long userId) {
        for (CoupleSpaceMember member : memberMapper.selectList(new LambdaQueryWrapper<CoupleSpaceMember>()
                .eq(CoupleSpaceMember::getUserId, userId)
                .eq(CoupleSpaceMember::getDeleted, 0))) {
            CoupleSpace space = coupleSpaceMapper.selectById(member.getSpaceId());
            if (space != null && !Integer.valueOf(1).equals(space.getDeleted())) {
                normalizeLegacySpace(space);
                if (SPACE_TYPE_COUPLE.equals(space.getSpaceType())) {
                    return space;
                }
            }
        }
        return null;
    }

    private CoupleSpace findUserPersonalSpace(Long userId) {
        for (CoupleSpaceMember member : memberMapper.selectList(new LambdaQueryWrapper<CoupleSpaceMember>()
                .eq(CoupleSpaceMember::getUserId, userId)
                .eq(CoupleSpaceMember::getDeleted, 0))) {
            CoupleSpace space = coupleSpaceMapper.selectById(member.getSpaceId());
            if (space != null && !Integer.valueOf(1).equals(space.getDeleted())) {
                normalizeLegacySpace(space);
                if (SPACE_TYPE_PERSONAL.equals(space.getSpaceType())) {
                    return space;
                }
            }
        }
        return null;
    }

    private CoupleSpace ensurePersonalSpaceExists(AppUser user) {
        CoupleSpace existingPersonalSpace = findUserPersonalSpace(user.getId());
        if (existingPersonalSpace != null) {
            return existingPersonalSpace;
        }
        return createPersonalSpaceForUser(user, false);
    }

    private CoupleSpace createPersonalSpaceForUser(AppUser user) {
        return createPersonalSpaceForUser(user, true);
    }

    private CoupleSpace createPersonalSpaceForUser(AppUser user, boolean activate) {
        CoupleSpace space = new CoupleSpace();
        space.setSpaceName(buildPersonalSpaceName(user));
        space.setSpaceType(SPACE_TYPE_PERSONAL);
        space.setStatus(SPACE_STATUS_ACTIVE);
        space.setCreatorUserId(user.getId());
        space.setMemberLimit(1);
        space.setDeleted(0);
        coupleSpaceMapper.insert(space);

        CoupleSpaceMember member = new CoupleSpaceMember();
        member.setSpaceId(space.getId());
        member.setUserId(user.getId());
        member.setRole("owner");
        member.setDeleted(0);
        memberMapper.insert(member);

        if (activate || user.getActiveSpaceId() == null) {
            user.setActiveSpaceId(space.getId());
            appUserMapper.updateById(user);
        }
        return space;
    }

    private String buildPersonalSpaceName(AppUser user) {
        return user.getNickname() + "\u7684\u65c5\u884c\u7a7a\u95f4";
    }

    private String buildWaitingCoupleSpaceName(AppUser user) {
        return user.getNickname() + "\u7684\u60c5\u4fa3\u65c5\u884c\u7a7a\u95f4";
    }

    private String buildCoupleSpaceName(Long creatorUserId, Long memberUserId) {
        AppUser creator = requireUser(creatorUserId);
        AppUser member = requireUser(memberUserId);
        return creator.getNickname() + "\u548c" + member.getNickname() + "\u7684\u65c5\u884c\u7a7a\u95f4";
    }

    private void normalizeLegacySpace(CoupleSpace space) {
        boolean changed = false;
        if (space.getSpaceType() == null || space.getSpaceType().isBlank()) {
            space.setSpaceType(countMembers(space.getId()) >= 2 ? SPACE_TYPE_COUPLE : SPACE_TYPE_PERSONAL);
            changed = true;
        }
        if (space.getStatus() == null || space.getStatus().isBlank()) {
            space.setStatus(SPACE_TYPE_COUPLE.equals(space.getSpaceType()) && countMembers(space.getId()) < 2
                    ? SPACE_STATUS_WAITING
                    : SPACE_STATUS_ACTIVE);
            changed = true;
        }
        if (SPACE_TYPE_PERSONAL.equals(space.getSpaceType()) && (space.getMemberLimit() == null || space.getMemberLimit() != 1)) {
            space.setMemberLimit(1);
            changed = true;
        }
        if (repairSpaceNameIfNeeded(space)) {
            changed = true;
        }
        if (changed) {
            coupleSpaceMapper.updateById(space);
        }
    }

    private boolean repairSpaceNameIfNeeded(CoupleSpace space) {
        if (SPACE_TYPE_PERSONAL.equals(space.getSpaceType())) {
            AppUser creator = requireUser(space.getCreatorUserId());
            String expectedName = buildPersonalSpaceName(creator);
            if (shouldRepairName(space.getSpaceName(), expectedName)) {
                space.setSpaceName(expectedName);
                return true;
            }
            return false;
        }

        if (SPACE_TYPE_COUPLE.equals(space.getSpaceType())) {
            if (SPACE_STATUS_WAITING.equals(space.getStatus()) || countMembers(space.getId()) < 2) {
                AppUser creator = requireUser(space.getCreatorUserId());
                String expectedName = buildWaitingCoupleSpaceName(creator);
                if (shouldRepairName(space.getSpaceName(), expectedName)) {
                    space.setSpaceName(expectedName);
                    return true;
                }
                return false;
            }

            List<CoupleSpaceMember> members = memberMapper.selectList(new LambdaQueryWrapper<CoupleSpaceMember>()
                    .eq(CoupleSpaceMember::getSpaceId, space.getId())
                    .eq(CoupleSpaceMember::getDeleted, 0)
                    .orderByAsc(CoupleSpaceMember::getId));
            if (members.size() >= 2) {
                String expectedName = buildCoupleSpaceName(members.get(0).getUserId(), members.get(1).getUserId());
                if (shouldRepairName(space.getSpaceName(), expectedName)) {
                    space.setSpaceName(expectedName);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean shouldRepairName(String currentName, String expectedName) {
        return currentName == null || currentName.isBlank() || !currentName.equals(expectedName);
    }
    private int countMembers(Long spaceId) {
        Long count = memberMapper.selectCount(new LambdaQueryWrapper<CoupleSpaceMember>()
                .eq(CoupleSpaceMember::getSpaceId, spaceId)
                .eq(CoupleSpaceMember::getDeleted, 0));
        return count.intValue();
    }

    private CoupleSpaceResponse toResponse(CoupleSpace space) {
        normalizeLegacySpace(space);
        return new CoupleSpaceResponse(
                space.getId(),
                space.getSpaceName(),
                space.getSpaceType(),
                space.getStatus(),
                space.getCreatorUserId(),
                countMembers(space.getId()),
                space.getMemberLimit(),
                true,
                isEditable(space));
    }

    private CoupleSpaceResponse toResponse(CoupleSpace space, AppUser user) {
        normalizeLegacySpace(space);
        return new CoupleSpaceResponse(
                space.getId(),
                space.getSpaceName(),
                space.getSpaceType(),
                space.getStatus(),
                space.getCreatorUserId(),
                countMembers(space.getId()),
                space.getMemberLimit(),
                space.getId().equals(user.getActiveSpaceId()),
                isEditable(space));
    }

    private boolean isEditable(CoupleSpace space) {
        return SPACE_TYPE_PERSONAL.equals(space.getSpaceType()) || SPACE_STATUS_ACTIVE.equals(space.getStatus());
    }

    private String generateCode() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            builder.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
        }
        return builder.toString();
    }

    private String generateAvailableCode() {
        for (int i = 0; i < 20; i++) {
            String code = generateCode();
            Boolean reserved = redisTemplate.opsForValue().setIfAbsent(inviteCodeKey(code), "reserved", INVITE_CODE_RESERVE_TTL);
            if (!Boolean.TRUE.equals(reserved)) {
                continue;
            }
            Long activeCount = inviteCodeMapper.selectCount(new LambdaQueryWrapper<InviteCode>()
                    .eq(InviteCode::getCode, code)
                    .eq(InviteCode::getDeleted, 0));
            if (activeCount == 0) {
                return code;
            }
            redisTemplate.delete(inviteCodeKey(code));
        }
        throw new ApiException("邀请码生成失败，请稍后再试");
    }

    private void cacheInviteCode(InviteCode inviteCode) {
        long ttlSeconds = Math.max(1, Duration.between(LocalDateTime.now(), inviteCode.getExpireAt()).getSeconds());
        redisTemplate.opsForValue().set(inviteCodeKey(inviteCode.getCode()), String.valueOf(inviteCode.getId()), Duration.ofSeconds(ttlSeconds));
    }

    private void expireInviteCodeIfNeeded(String code) {
        InviteCode inviteCode = inviteCodeMapper.selectOne(new LambdaQueryWrapper<InviteCode>()
                .eq(InviteCode::getCode, code)
                .eq(InviteCode::getDeleted, 0));
        if (inviteCode != null && "unused".equals(inviteCode.getStatus()) && inviteCode.getExpireAt().isBefore(LocalDateTime.now())) {
            inviteCode.setStatus("expired");
            inviteCodeMapper.updateById(inviteCode);
        }
    }

    private String inviteCodeKey(String code) {
        return INVITE_CODE_KEY_PREFIX + code;
    }
}
