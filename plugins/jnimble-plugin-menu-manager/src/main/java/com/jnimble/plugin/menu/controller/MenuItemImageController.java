package com.jnimble.plugin.menu.controller;

import com.jnimble.platform.auth.ControllerAuthorization;
import com.jnimble.plugin.menu.service.MenuItemImageStorageService;
import java.time.Duration;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/admin/plugins/menu-manager/items/images")
public class MenuItemImageController {

    private static final String IMAGE_BASE_PATH = "/admin/plugins/menu-manager/items/images/";

    private final MenuItemImageStorageService storageService;
    private final ControllerAuthorization authorization;

    public MenuItemImageController(
            MenuItemImageStorageService storageService,
            ControllerAuthorization authorization
    ) {
        this.storageService = storageService;
        this.authorization = authorization;
    }

    @PostMapping
    @ResponseBody
    public ResponseEntity<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        authorization.requirePermission("menu-manager.item.manage");
        try {
            MenuItemImageStorageService.StoredImage storedImage = storageService.store(file);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "imagePath", IMAGE_BASE_PATH + storedImage.fileName()
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", ex.getMessage()
            ));
        }
    }

    @GetMapping("/{fileName:.+}")
    @ResponseBody
    public ResponseEntity<Resource> image(@PathVariable String fileName) {
        try {
            Resource resource = storageService.load(fileName);
            return ResponseEntity.ok()
                    .contentType(storageService.mediaType(fileName))
                    .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic())
                    .header("X-Content-Type-Options", "nosniff")
                    .body(resource);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "图片不存在", ex);
        }
    }
}
