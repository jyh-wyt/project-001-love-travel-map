package com.lovetravel.server;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.lovetravel.server.modules.auth.mapper.AppUserMapper;
import com.lovetravel.server.modules.oss.service.OssService;
import com.lovetravel.server.modules.space.domain.CoupleSpace;
import com.lovetravel.server.modules.space.mapper.CoupleSpaceMapper;
import com.lovetravel.server.modules.space.mapper.CoupleSpaceMemberMapper;
import com.lovetravel.server.modules.space.service.SpaceService;
import com.lovetravel.server.modules.travel.domain.Trip;
import com.lovetravel.server.modules.travel.domain.TripImage;
import com.lovetravel.server.modules.travel.domain.TripPost;
import com.lovetravel.server.modules.travel.mapper.TripImageMapper;
import com.lovetravel.server.modules.travel.mapper.TripMapper;
import com.lovetravel.server.modules.travel.mapper.TripPostMapper;
import com.lovetravel.server.modules.travel.service.TravelService;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class TravelServiceSpaceIsolationTest {

    @Test
    void cityTravelReadsNestedPostsAndImagesWithSpaceFilter() {
        initTableInfo(TripPost.class);
        initTableInfo(TripImage.class);

        AppUserMapper appUserMapper = Mockito.mock(AppUserMapper.class);
        CoupleSpaceMapper coupleSpaceMapper = Mockito.mock(CoupleSpaceMapper.class);
        CoupleSpaceMemberMapper memberMapper = Mockito.mock(CoupleSpaceMemberMapper.class);
        TripMapper tripMapper = Mockito.mock(TripMapper.class);
        TripPostMapper postMapper = Mockito.mock(TripPostMapper.class);
        TripImageMapper imageMapper = Mockito.mock(TripImageMapper.class);
        OssService ossService = Mockito.mock(OssService.class);
        SpaceService spaceService = Mockito.mock(SpaceService.class);
        TravelService travelService = new TravelService(
                appUserMapper,
                coupleSpaceMapper,
                memberMapper,
                tripMapper,
                postMapper,
                imageMapper,
                ossService,
                spaceService);

        CoupleSpace space = new CoupleSpace();
        space.setId(11L);
        Mockito.when(spaceService.requireActiveSpace(7L)).thenReturn(space);

        Trip trip = new Trip();
        trip.setId(22L);
        trip.setSpaceId(11L);
        trip.setProvinceCode("370000");
        trip.setProvinceName("山东省");
        trip.setCityCode("370200");
        trip.setCityName("青岛市");
        trip.setTitle("青岛市旅行记录");
        trip.setDeleted(0);
        Mockito.when(tripMapper.selectOne(any())).thenReturn(trip);

        TripPost post = new TripPost();
        post.setId(33L);
        post.setSpaceId(11L);
        post.setTripId(22L);
        post.setAuthorUserId(7L);
        post.setContent("去海边散步");
        post.setDeleted(0);
        Mockito.when(postMapper.selectList(any())).thenReturn(List.of(post));
        Mockito.when(imageMapper.selectList(any())).thenReturn(List.of());
        Mockito.when(imageMapper.selectOne(any())).thenReturn(null);

        travelService.getOrCreateCityTravel(7L, "370200", "370000", "山东省", "青岛市");

        ArgumentCaptor<LambdaQueryWrapper<TripPost>> postQueryCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        Mockito.verify(postMapper).selectList(postQueryCaptor.capture());
        assertQueryContainsSpaceId(postQueryCaptor.getValue());

        ArgumentCaptor<LambdaQueryWrapper<TripImage>> imageListQueryCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        Mockito.verify(imageMapper).selectList(imageListQueryCaptor.capture());
        assertQueryContainsSpaceId(imageListQueryCaptor.getValue());

        ArgumentCaptor<LambdaQueryWrapper<TripImage>> coverQueryCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        Mockito.verify(imageMapper).selectOne(coverQueryCaptor.capture());
        assertQueryContainsSpaceId(coverQueryCaptor.getValue());
    }

    private void assertQueryContainsSpaceId(LambdaQueryWrapper<?> wrapper) {
        assertTrue(wrapper.getCustomSqlSegment().contains("space_id"), wrapper.getCustomSqlSegment());
    }

    private void initTableInfo(Class<?> entityClass) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), entityClass.getName());
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}
