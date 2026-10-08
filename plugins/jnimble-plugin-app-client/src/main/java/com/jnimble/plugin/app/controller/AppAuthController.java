package com.jnimble.plugin.app.controller;

import com.jnimble.plugin.app.model.dto.ApiResult;
import com.jnimble.plugin.app.service.AppAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app/auth")
public class AppAuthController {

    private final AppAuthService authService;

    public AppAuthController(AppAuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Map<String, Object> login(HttpServletResponse response,
                                     @RequestBody(required = false) Map<String, String> body) {
        return AppApiExecutor.guard(response, () -> {
            Map<String, String> payload = body == null ? Map.of() : body;
            return ApiResult.success(authService.login(payload.get("username"), payload.get("password")));
        });
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest request, HttpServletResponse response) {
        return AppApiExecutor.guard(response, () -> {
            authService.logout(request);
            return ApiResult.success(new LinkedHashMap<>());
        });
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpServletRequest request, HttpServletResponse response) {
        return AppApiExecutor.guard(response, () -> {
            AppAuthService.AppSession session = authService.authenticate(request);
            Map<String, Object> user = new LinkedHashMap<>();
            user.put("id", session.userId());
            user.put("username", session.username());
            user.put("displayName", session.displayName());
            user.put("permissions", authService.grantedPermissions(session.username()));
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("user", user);
            return ApiResult.success(data);
        });
    }
}
