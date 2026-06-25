package com.lovetravel.server.modules.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lovetravel.server.common.ApiException;
import com.lovetravel.server.modules.auth.mapper.AppUserMapper;
import com.lovetravel.server.modules.auth.mapper.UserAuthIdentityMapper;
import com.lovetravel.server.modules.auth.domain.AppUser;
import com.lovetravel.server.modules.auth.domain.UserAuthIdentity;
import com.lovetravel.server.modules.space.service.SpaceService;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.lovetravel.server.modules.auth.vo.AuthResponse;
import com.lovetravel.server.modules.auth.dto.LoginRequest;
import com.lovetravel.server.modules.auth.dto.RegisterRequest;
import com.lovetravel.server.modules.auth.dto.UpdateNicknameRequest;
import com.lovetravel.server.modules.auth.dto.UpdatePasswordRequest;

@Service
public class AuthService {

    private static final String LOGIN_FAIL_KEY_PREFIX = "love-travel:login:fail:";
    private static final String LOGIN_LOCK_KEY_PREFIX = "love-travel:login:lock:";
    private static final int LOGIN_FAIL_LIMIT = 5;
    private static final Duration LOGIN_FAIL_WINDOW = Duration.ofMinutes(10);
    private static final Duration LOGIN_LOCK_TTL = Duration.ofMinutes(10);

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private final AppUserMapper appUserMapper;
    private final UserAuthIdentityMapper userAuthIdentityMapper;
    private final SpaceService spaceService;
    private final StringRedisTemplate redisTemplate;

    public AuthService(
            AppUserMapper appUserMapper,
            UserAuthIdentityMapper userAuthIdentityMapper,
            SpaceService spaceService,
            StringRedisTemplate redisTemplate) {
        this.appUserMapper = appUserMapper;
        this.userAuthIdentityMapper = userAuthIdentityMapper;
        this.spaceService = spaceService;
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String account = normalizeAccount(request.getAccount());
        String username = normalizeUsername(request.getNickname());
        validatePassword(request.getPassword());
        validateConfirmPassword(request.getPassword(), request.getConfirmPassword());

        UserAuthIdentity existing = findPasswordIdentity(account);
        if (existing != null) {
            throw new ApiException("账号已存在，请直接登录");
        }
        if (isUsernameTaken(username)) {
            throw new ApiException("用户名已存在，请换一个");
        }

        AppUser user = new AppUser();
        user.setNickname(username);
        user.setUserType("account");
        user.setStatus("active");
        user.setDeleted(0);
        appUserMapper.insert(user);

        UserAuthIdentity identity = new UserAuthIdentity();
        identity.setUserId(user.getId());
        identity.setIdentityType("password");
        identity.setIdentityValue(account);
        identity.setCredentialHash(passwordEncoder.encode(request.getPassword()));
        identity.setVerified(1);
        identity.setDeleted(0);
        userAuthIdentityMapper.insert(identity);
        spaceService.ensureActiveSpace(user.getId());

        return new AuthResponse(user.getId(), account, user.getNickname());
    }

    public AuthResponse login(LoginRequest request) {
        String account = normalizeAccount(request.getAccount());
        ensureLoginNotLocked(account);
        UserAuthIdentity identity = findPasswordIdentity(account);
        if (identity == null || !passwordEncoder.matches(request.getPassword(), identity.getCredentialHash())) {
            recordLoginFailure(account);
        }

        AppUser user = appUserMapper.selectById(identity.getUserId());
        if (user == null || Integer.valueOf(1).equals(user.getDeleted())) {
            throw new ApiException("账号不存在或已停用");
        }

        clearLoginFailure(account);
        return new AuthResponse(user.getId(), account, user.getNickname());
    }

    public AuthResponse getCurrentUser(Long userId) {
        AppUser user = requireUser(userId);
        UserAuthIdentity identity = requirePasswordIdentityByUserId(userId);
        return new AuthResponse(user.getId(), identity.getIdentityValue(), user.getNickname());
    }

    @Transactional
    public AuthResponse updateNickname(Long userId, UpdateNicknameRequest request) {
        AppUser user = requireUser(userId);
        String username = normalizeUsername(request.getNickname());
        if (isUsernameTakenByOtherUser(username, userId)) {
            throw new ApiException("用户名已存在，请换一个");
        }
        user.setNickname(username);
        appUserMapper.updateById(user);

        UserAuthIdentity identity = requirePasswordIdentityByUserId(userId);
        return new AuthResponse(user.getId(), identity.getIdentityValue(), user.getNickname());
    }

    @Transactional
    public AuthResponse updatePassword(Long userId, UpdatePasswordRequest request) {
        AppUser user = requireUser(userId);
        UserAuthIdentity identity = requirePasswordIdentityByUserId(userId);
        if (request.getOldPassword() == null || !passwordEncoder.matches(request.getOldPassword(), identity.getCredentialHash())) {
            throw new ApiException("原密码不正确");
        }
        validatePassword(request.getNewPassword());
        validateConfirmPassword(request.getNewPassword(), request.getConfirmPassword());
        identity.setCredentialHash(passwordEncoder.encode(request.getNewPassword()));
        userAuthIdentityMapper.updateById(identity);
        return new AuthResponse(user.getId(), identity.getIdentityValue(), user.getNickname());
    }

    private UserAuthIdentity findPasswordIdentity(String account) {
        return userAuthIdentityMapper.selectOne(new LambdaQueryWrapper<UserAuthIdentity>()
                .eq(UserAuthIdentity::getIdentityType, "password")
                .eq(UserAuthIdentity::getIdentityValue, account)
                .eq(UserAuthIdentity::getDeleted, 0));
    }

    private UserAuthIdentity requirePasswordIdentityByUserId(Long userId) {
        UserAuthIdentity identity = userAuthIdentityMapper.selectOne(new LambdaQueryWrapper<UserAuthIdentity>()
                .eq(UserAuthIdentity::getUserId, userId)
                .eq(UserAuthIdentity::getIdentityType, "password")
                .eq(UserAuthIdentity::getDeleted, 0)
                .last("LIMIT 1"));
        if (identity == null) {
            throw new ApiException("账号登录信息不存在");
        }
        return identity;
    }

    private AppUser requireUser(Long userId) {
        AppUser user = appUserMapper.selectById(userId);
        if (user == null || Integer.valueOf(1).equals(user.getDeleted())) {
            throw new ApiException("用户不存在");
        }
        return user;
    }

    private String normalizeAccount(String account) {
        if (account == null || account.isBlank()) {
            throw new ApiException("请输入账号");
        }
        String normalized = account.trim().toLowerCase();
        if (normalized.length() < 5 || normalized.length() > 20) {
            throw new ApiException("账号长度需要在 5 到 20 个字符之间");
        }
        if (!normalized.matches("^[a-zA-Z0-9_]+$")) {
            throw new ApiException("账号只能使用英文字母、数字和下划线");
        }
        return normalized;
    }

    private String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new ApiException("请输入用户名");
        }
        String normalized = username.trim();
        if (normalized.length() < 2 || normalized.length() > 20) {
            throw new ApiException("用户名长度需要在 2 到 20 个字符之间");
        }
        return normalized;
    }

    private boolean isUsernameTaken(String username) {
        Long count = appUserMapper.selectCount(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getNickname, username)
                .eq(AppUser::getDeleted, 0));
        return count > 0;
    }

    private boolean isUsernameTakenByOtherUser(String username, Long userId) {
        Long count = appUserMapper.selectCount(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getNickname, username)
                .ne(AppUser::getId, userId)
                .eq(AppUser::getDeleted, 0));
        return count > 0;
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 40) {
            throw new ApiException("密码长度需要在 8 到 40 个字符之间");
        }
    }

    private void validateConfirmPassword(String password, String confirmPassword) {
        if (confirmPassword == null || confirmPassword.isBlank()) {
            throw new ApiException("请再次输入确认密码");
        }
        if (!password.equals(confirmPassword)) {
            throw new ApiException("两次输入的密码不一致");
        }
    }

    private void ensureLoginNotLocked(String account) {
        if (Boolean.TRUE.equals(redisTemplate.hasKey(loginLockKey(account)))) {
            throw new ApiException("账号已临时锁定，请 10 分钟后再试");
        }
    }

    private void recordLoginFailure(String account) {
        String failKey = loginFailKey(account);
        Long failedTimes = redisTemplate.opsForValue().increment(failKey);
        if (failedTimes != null && failedTimes == 1L) {
            redisTemplate.expire(failKey, LOGIN_FAIL_WINDOW);
        }
        if (failedTimes != null && failedTimes >= LOGIN_FAIL_LIMIT) {
            redisTemplate.opsForValue().set(loginLockKey(account), "1", LOGIN_LOCK_TTL);
            redisTemplate.delete(failKey);
            throw new ApiException("账号或密码不正确，账号已临时锁定 10 分钟");
        }
        long remaining = LOGIN_FAIL_LIMIT - (failedTimes == null ? 1L : failedTimes);
        throw new ApiException("账号或密码不正确，还可尝试 " + remaining + " 次");
    }

    private void clearLoginFailure(String account) {
        redisTemplate.delete(loginFailKey(account));
        redisTemplate.delete(loginLockKey(account));
    }

    private String loginFailKey(String account) {
        return LOGIN_FAIL_KEY_PREFIX + account;
    }

    private String loginLockKey(String account) {
        return LOGIN_LOCK_KEY_PREFIX + account;
    }

}
