package com.jnimble.plugin.app.controller;

import com.jnimble.plugin.app.model.dto.ApiResult;
import com.jnimble.plugin.app.service.AppForbiddenException;
import com.jnimble.plugin.app.service.AppUnauthorizedException;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AppApiExecutor {

    private static final Logger log = LoggerFactory.getLogger(AppApiExecutor.class);

    private AppApiExecutor() {
    }

    public static Map<String, Object> guard(HttpServletResponse response, Supplier<Map<String, Object>> action) {
        try {
            return action.get();
        } catch (AppUnauthorizedException ex) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return ApiResult.error(401, message(ex));
        } catch (AppForbiddenException ex) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return ApiResult.error(403, message(ex));
        } catch (IllegalArgumentException ex) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return ApiResult.error(400, message(ex));
        } catch (IllegalStateException ex) {
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            return ApiResult.error(409, message(ex));
        } catch (Exception ex) {
            log.error("App API request failed", ex);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return ApiResult.error(500, "服务异常");
        }
    }

    private static String message(RuntimeException exception) {
        return exception.getMessage() == null ? "操作失败" : exception.getMessage();
    }
}
