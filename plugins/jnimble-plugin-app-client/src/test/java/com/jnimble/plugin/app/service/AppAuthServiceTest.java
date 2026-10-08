package com.jnimble.plugin.app.service;

import com.jnimble.platform.auth.UserAccountService;
import com.jnimble.platform.auth.UserRecord;
import com.jnimble.platform.auth.UserStatus;
import com.jnimble.platform.permission.AuthorizationService;
import com.jnimble.platform.permission.PermissionRecord;
import com.jnimble.platform.permission.PermissionService;
import com.jnimble.platform.permission.PermissionStatus;
import com.jnimble.platform.permission.PluginPermissionGroup;
import com.jnimble.platform.persistence.crud.MapperUtils;
import com.jnimble.plugin.app.mapper.AppAuthTokenMapper;
import com.jnimble.plugin.app.model.entity.AppAuthTokenEntity;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppAuthServiceTest {

    @Mock
    private UserAccountService userAccountService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private PermissionService permissionService;

    @Mock
    private AppAuthTokenMapper tokenMapper;

    @InjectMocks
    private AppAuthService authService;

    private MockedStatic<MapperUtils> mapperUtilsMock;

    @BeforeEach
    void setUp() {
        mapperUtilsMock = mockStatic(MapperUtils.class);
    }

    @AfterEach
    void tearDown() {
        mapperUtilsMock.close();
    }

    @Test
    void loginIssuesTokenForActiveUser() {
        UserRecord user = new UserRecord(
                "u-1", "alice", "encoded", "Alice",
                UserStatus.ACTIVE, Instant.now(), Instant.now());
        when(userAccountService.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "encoded")).thenReturn(true);
        when(permissionService.listPermissionsByPlugin()).thenReturn(List.of(
                group("menu-manager", permission("menu-manager.item.manage", PermissionStatus.AVAILABLE))));
        when(authorizationService.hasPermission("alice", "menu-manager.item.manage")).thenReturn(true);
        mapperUtilsMock.when(() -> MapperUtils.insert(eq(tokenMapper), any(AppAuthTokenEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));

        Map<String, Object> data = authService.login("alice", "secret");

        assertNotNull(data.get("token"));
        String token = (String) data.get("token");
        assertEquals(64, token.length());
        assertTrue(token.matches("[0-9a-f]{64}"));

        ArgumentCaptor<AppAuthTokenEntity> captor = ArgumentCaptor.forClass(AppAuthTokenEntity.class);
        mapperUtilsMock.verify(() -> MapperUtils.insert(eq(tokenMapper), captor.capture()));
        AppAuthTokenEntity stored = captor.getValue();
        assertEquals("u-1", stored.getUserId());
        assertEquals("alice", stored.getUsername());
        assertEquals("ACTIVE", stored.getStatus());
        assertTrue(stored.getExpiresAt().isAfter(LocalDateTime.now().plusDays(29)));

        Map<String, Object> userInfo = (Map<String, Object>) data.get("user");
        assertEquals("alice", userInfo.get("username"));
        assertEquals(List.of("menu-manager.item.manage"), userInfo.get("permissions"));
    }

    @Test
    void loginRejectsWrongPassword() {
        UserRecord user = new UserRecord(
                "u-1", "alice", "encoded", "Alice",
                UserStatus.ACTIVE, Instant.now(), Instant.now());
        when(userAccountService.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

        assertThrows(AppUnauthorizedException.class, () -> authService.login("alice", "wrong"));
    }

    @Test
    void loginRejectsDisabledUser() {
        UserRecord user = new UserRecord(
                "u-1", "alice", "encoded", "Alice",
                UserStatus.DISABLED, Instant.now(), Instant.now());
        when(userAccountService.findByUsername("alice")).thenReturn(Optional.of(user));

        AppUnauthorizedException ex = assertThrows(AppUnauthorizedException.class,
                () -> authService.login("alice", "secret"));
        assertEquals("账号已停用", ex.getMessage());
    }

    @Test
    void authenticateRejectsMissingHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThrows(AppUnauthorizedException.class, () -> authService.authenticate(request));
    }

    @Test
    void authenticateAcceptsBearerTokenAndRefreshesLastUsed() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token-123");
        AppAuthTokenEntity entity = activeToken();
        mapperUtilsMock.when(() -> MapperUtils.selectOne(eq(tokenMapper), eq(AppAuthTokenEntity.class), any()))
                .thenReturn(entity);

        AppAuthService.AppSession session = authService.authenticate(request);

        assertEquals("alice", session.username());
        assertEquals("token-123", session.token());
        mapperUtilsMock.verify(() -> MapperUtils.updateById(eq(tokenMapper), any(AppAuthTokenEntity.class)));
    }

    @Test
    void authenticateRejectsExpiredToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "token-123");
        AppAuthTokenEntity entity = activeToken();
        entity.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        mapperUtilsMock.when(() -> MapperUtils.selectOne(eq(tokenMapper), eq(AppAuthTokenEntity.class), any()))
                .thenReturn(entity);

        assertThrows(AppUnauthorizedException.class, () -> authService.authenticate(request));
    }

    @Test
    void requirePermissionRejectsMissingPermission() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "token-123");
        mapperUtilsMock.when(() -> MapperUtils.selectOne(eq(tokenMapper), eq(AppAuthTokenEntity.class), any()))
                .thenReturn(activeToken());
        when(authorizationService.hasPermission("alice", "printer-core.config")).thenReturn(false);

        assertThrows(AppForbiddenException.class,
                () -> authService.requirePermission(request, "printer-core.config"));
    }

    @Test
    void grantedPermissionsFiltersUnavailableAndUngranted() {
        when(permissionService.listPermissionsByPlugin()).thenReturn(List.of(
                group("menu-manager",
                        permission("menu-manager.item.manage", PermissionStatus.AVAILABLE),
                        permission("menu-manager.category.manage", PermissionStatus.UNAVAILABLE)),
                group("printer-core",
                        permission("printer-core.config", PermissionStatus.AVAILABLE))));
        when(authorizationService.hasPermission("alice", "menu-manager.item.manage")).thenReturn(true);
        when(authorizationService.hasPermission("alice", "printer-core.config")).thenReturn(false);

        List<String> granted = authService.grantedPermissions("alice");

        assertEquals(List.of("menu-manager.item.manage"), granted);
    }

    private AppAuthTokenEntity activeToken() {
        AppAuthTokenEntity entity = new AppAuthTokenEntity();
        entity.setId(1L);
        entity.setToken("token-123");
        entity.setUserId("u-1");
        entity.setUsername("alice");
        entity.setDisplayName("Alice");
        entity.setStatus("ACTIVE");
        entity.setExpiresAt(LocalDateTime.now().plusDays(1));
        return entity;
    }

    private PluginPermissionGroup group(String pluginId, PermissionRecord... permissions) {
        return new PluginPermissionGroup(pluginId, List.of(permissions));
    }

    private PermissionRecord permission(String code, PermissionStatus status) {
        return new PermissionRecord(
                code.substring(0, code.indexOf('.')), code, code, null, null, null, status, Instant.now());
    }
}
