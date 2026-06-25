package com.lovetravel.server;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import com.lovetravel.server.modules.auth.service.AuthService;
import com.lovetravel.server.modules.auth.dto.RegisterRequest;
import com.lovetravel.server.modules.auth.mapper.AppUserMapper;
import com.lovetravel.server.modules.auth.mapper.UserAuthIdentityMapper;
import com.lovetravel.server.modules.auth.domain.AppUser;
import com.lovetravel.server.modules.space.service.SpaceService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AuthServiceTest {

    @Test
    void registerCreatesPersonalSpaceForNewUser() {
        AppUserMapper appUserMapper = Mockito.mock(AppUserMapper.class);
        UserAuthIdentityMapper userAuthIdentityMapper = Mockito.mock(UserAuthIdentityMapper.class);
        SpaceService spaceService = Mockito.mock(SpaceService.class);
        StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
        AuthService authService = new AuthService(appUserMapper, userAuthIdentityMapper, spaceService, redisTemplate);

        Mockito.when(appUserMapper.insert(any(AppUser.class))).thenAnswer(invocation -> {
            AppUser user = invocation.getArgument(0);
            user.setId(42L);
            return 1;
        });

        RegisterRequest request = new RegisterRequest();
        request.setAccount("traveler_01");
        request.setNickname("焦焦");
        request.setPassword("password123");
        request.setConfirmPassword("password123");

        authService.register(request);

        Mockito.verify(spaceService).ensureActiveSpace(eq(42L));
    }
}
