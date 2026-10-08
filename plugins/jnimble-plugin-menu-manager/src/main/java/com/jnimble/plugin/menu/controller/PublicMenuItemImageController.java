package com.jnimble.plugin.menu.controller;

import com.jnimble.plugin.menu.service.MenuItemImageStorageService;
import java.time.Duration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/api/menu/images")
public class PublicMenuItemImageController {

    private final MenuItemImageStorageService storageService;

    public PublicMenuItemImageController(MenuItemImageStorageService storageService) {
        this.storageService = storageService;
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
