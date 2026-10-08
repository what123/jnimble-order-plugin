package com.jnimble.plugin.app.service;

import com.jnimble.platform.auth.UserAccountService;
import com.jnimble.platform.auth.UserRecord;
import com.jnimble.platform.auth.UserStatus;
import com.jnimble.platform.permission.AuthorizationService;
import com.jnimble.platform.permission.PermissionRecord;
import com.jnimble.platform.permission.PermissionService;
import com.jnimble.platform.permission.PluginPermissionGroup;
import com.jnimble.platform.persistence.crud.MapperUtils;
import com.jnimble.plugin.app.mapper.AppAuthTokenMapper;
import com.jnimble.plugin.app.model.entity.AppAuthTokenEntity;
import jakarta.servlet.http.HttpServletRequest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppAuthService {

    private static final Duration TOKEN_TTL = Duration.ofDays(30);
    private static final Duration LAST_USED_WRITE_INTERVAL = Duration.ofMinutes(10);

    private final UserAccountService userAccountService;
    private final PasswordEncoder passwordEncoder;
    private final AuthorizationService authorizationService;
    private final PermissionService permissionService;
    private final AppAuthTokenMapper tokenMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public AppAuthService(UserAccountService userAccountService,
                          PasswordEncoder passwordEncoder,
                          AuthorizationService authorizationService,
                          PermissionService permissionService,
                          AppAuthTokenMapper tokenMapper) {
        this.userAccountService = userAccountService;
        this.passwordEncoder = passwordEncoder;
        this.authorizationService = authorizationService;
        this.permissionService = permissionService;
        this.tokenMapper = tokenMapper;
    }

    @Transactional
    public Map<String, Object> login(String username, String rawPassword) {
        if (username == null || username.isBlank() || rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("请输入用户名和密码");
        }
        UserRecord user = userAccountService.findByUsername(username.trim())
                .orElseThrow(() -> new AppUnauthorizedException("用户名或密码错误"));
        if (user.status() != UserStatus.ACTIVE) {
            throw new AppUnauthorizedException("账号已停用");
        }
        if (!passwordEncoder.matches(rawPassword, user.passwordHash())) {
            throw new AppUnauthorizedException("用户名或密码错误");
        }

        LocalDateTime now = LocalDateTime.now();
        AppAuthTokenEntity entity = new AppAuthTokenEntity();
        entity.setToken(newToken());
        entity.setUserId(user.id());
        entity.setUsername(user.username());
        entity.setDisplayName(user.displayName());
        entity.setStatus("ACTIVE");
        entity.setExpiresAt(now.plus(TOKEN_TTL));
        entity.setCreatedAt(now);
        MapperUtils.insert(tokenMapper, entity);

        Map<String, Object> userInfo = new LinkedHashMap<>();
        userInfo.put("id", user.id());
        userInfo.put("username", user.username());
        userInfo.put("displayName", user.displayName());
        userInfo.put("permissions", grantedPermissions(user.username()));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", entity.getToken());
        data.put("expiresAt", entity.getExpiresAt().toString());
        data.put("user", userInfo);
        return data;
    }

    @Transactional
    public AppSession authenticate(HttpServletRequest request) {
        String token = resolveToken(request);
        if (token == null) {
            throw new AppUnauthorizedException("未登录");
        }
        AppAuthTokenEntity entity = MapperUtils.selectOne(tokenMapper, AppAuthTokenEntity.class,
                wrapper -> wrapper.eq("token", token).eq("status", "ACTIVE"));
        if (entity == null) {
            throw new AppUnauthorizedException("登录已失效，请重新登录");
        }
        LocalDateTime now = LocalDateTime.now();
        if (entity.getExpiresAt() == null || entity.getExpiresAt().isBefore(now)) {
            throw new AppUnauthorizedException("登录已过期，请重新登录");
        }
        if (entity.getLastUsedAt() == null || entity.getLastUsedAt().isBefore(now.minus(LAST_USED_WRITE_INTERVAL))) {
            AppAuthTokenEntity update = new AppAuthTokenEntity();
            update.setId(entity.getId());
            update.setLastUsedAt(now);
            MapperUtils.updateById(tokenMapper, update);
        }
        return new AppSession(entity.getUserId(), entity.getUsername(), entity.getDisplayName(), token);
    }

    public AppSession requirePermission(HttpServletRequest request, String permissionCode) {
        AppSession session = authenticate(request);
        if (permissionCode != null && !permissionCode.isBlank()
                && !authorizationService.hasPermission(session.username(), permissionCode)) {
            throw new AppForbiddenException("无操作权限");
        }
        return session;
    }

    public AppSession requireAnyPermission(HttpServletRequest request, String... permissionCodes) {
        AppSession session = authenticate(request);
        if (permissionCodes == null || permissionCodes.length == 0) {
            return session;
        }
        for (String permissionCode : permissionCodes) {
            if (permissionCode != null && !permissionCode.isBlank()
                    && authorizationService.hasPermission(session.username(), permissionCode)) {
                return session;
            }
        }
        throw new AppForbiddenException("无操作权限");
    }

    @Transactional
    public void logout(HttpServletRequest request) {
        String token = resolveToken(request);
        if (token == null) {
            return;
        }
        AppAuthTokenEntity entity = MapperUtils.selectOne(tokenMapper, AppAuthTokenEntity.class,
                wrapper -> wrapper.eq("token", token).eq("status", "ACTIVE"));
        if (entity != null) {
            AppAuthTokenEntity update = new AppAuthTokenEntity();
            update.setId(entity.getId());
            update.setStatus("REVOKED");
            MapperUtils.updateById(tokenMapper, update);
        }
    }

    public List<String> grantedPermissions(String username) {
        List<String> granted = new ArrayList<>();
        for (PluginPermissionGroup group : permissionService.listPermissionsByPlugin()) {
            for (PermissionRecord permission : group.permissions()) {
                if (permission.available() && authorizationService.hasPermission(username, permission.code())) {
                    granted.add(permission.code());
                }
            }
        }
        return granted;
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || header.isBlank()) {
            return null;
        }
        String token = header.trim();
        if (token.regionMatches(true, 0, "Bearer ", 0, 7)) {
            token = token.substring(7).trim();
        }
        return token.isEmpty() ? null : token;
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public record AppSession(String userId, String username, String displayName, String token) {
    }
}
