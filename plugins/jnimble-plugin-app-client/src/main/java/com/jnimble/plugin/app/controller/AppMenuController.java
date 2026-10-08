package com.jnimble.plugin.app.controller;

import com.jnimble.plugin.app.model.dto.ApiResult;
import com.jnimble.plugin.app.service.AppAuthService;
import com.jnimble.plugin.menu.model.entity.CategoryEntity;
import com.jnimble.plugin.menu.model.entity.MenuItemEntity;
import com.jnimble.plugin.menu.model.entity.MenuItemImageEntity;
import com.jnimble.plugin.menu.service.CategoryService;
import com.jnimble.plugin.menu.service.MenuItemImageStorageService;
import com.jnimble.plugin.menu.service.MenuItemImageUrls;
import com.jnimble.plugin.menu.service.MenuItemService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/app/menu")
public class AppMenuController {

    private static final String ITEM_MANAGE = "menu-manager.item.manage";
    private static final String CATEGORY_MANAGE = "menu-manager.category.manage";

    private final MenuItemService menuItemService;
    private final CategoryService categoryService;
    private final MenuItemImageStorageService storageService;
    private final AppAuthService authService;

    public AppMenuController(MenuItemService menuItemService,
                             CategoryService categoryService,
                             MenuItemImageStorageService storageService,
                             AppAuthService authService) {
        this.menuItemService = menuItemService;
        this.categoryService = categoryService;
        this.storageService = storageService;
        this.authService = authService;
    }

    @GetMapping("/categories")
    public Map<String, Object> listCategories(HttpServletRequest request, HttpServletResponse response) {
        return AppApiExecutor.guard(response, () -> {
            authService.requireAnyPermission(request, ITEM_MANAGE, CATEGORY_MANAGE);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("categories", categoryService.listCategories());
            return ApiResult.success(data);
        });
    }

    @PostMapping("/categories")
    public Map<String, Object> createCategory(HttpServletRequest request, HttpServletResponse response,
                                              @RequestBody CategoryEntity entity) {
        return AppApiExecutor.guard(response, () -> {
            authService.requirePermission(request, CATEGORY_MANAGE);
            return ApiResult.success(categoryService.createCategory(entity));
        });
    }

    @PutMapping("/categories/{id}")
    public Map<String, Object> updateCategory(HttpServletRequest request, HttpServletResponse response,
                                              @PathVariable Long id, @RequestBody CategoryEntity entity) {
        return AppApiExecutor.guard(response, () -> {
            authService.requirePermission(request, CATEGORY_MANAGE);
            entity.setId(id);
            return ApiResult.success(categoryService.updateCategory(entity));
        });
    }

    @DeleteMapping("/categories/{id}")
    public Map<String, Object> deleteCategory(HttpServletRequest request, HttpServletResponse response,
                                              @PathVariable Long id) {
        return AppApiExecutor.guard(response, () -> {
            authService.requirePermission(request, CATEGORY_MANAGE);
            categoryService.deleteCategory(id);
            return ApiResult.success(new LinkedHashMap<>());
        });
    }

    @GetMapping("/items")
    public Map<String, Object> listItems(HttpServletRequest request, HttpServletResponse response,
                                         @RequestParam(required = false) Long categoryId,
                                         @RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) String status) {
        return AppApiExecutor.guard(response, () -> {
            authService.requireAnyPermission(request, ITEM_MANAGE, CATEGORY_MANAGE);
            List<MenuItemEntity> items = menuItemService.listItems(categoryId, keyword, status);
            items.forEach(this::normalizeImages);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("items", items);
            return ApiResult.success(data);
        });
    }

    @PostMapping("/items")
    public Map<String, Object> createItem(HttpServletRequest request, HttpServletResponse response,
                                          @RequestBody MenuItemEntity entity) {
        return AppApiExecutor.guard(response, () -> {
            authService.requirePermission(request, ITEM_MANAGE);
            return ApiResult.success(normalizeImages(menuItemService.createItem(entity)));
        });
    }

    @PutMapping("/items/{id}")
    public Map<String, Object> updateItem(HttpServletRequest request, HttpServletResponse response,
                                          @PathVariable Long id, @RequestBody MenuItemEntity entity) {
        return AppApiExecutor.guard(response, () -> {
            authService.requirePermission(request, ITEM_MANAGE);
            entity.setId(id);
            return ApiResult.success(normalizeImages(menuItemService.updateItem(entity)));
        });
    }

    @PostMapping("/items/batch-status")
    public Map<String, Object> batchUpdateStatus(HttpServletRequest request, HttpServletResponse response,
                                                 @RequestBody Map<String, Object> body) {
        return AppApiExecutor.guard(response, () -> {
            authService.requirePermission(request, ITEM_MANAGE);
            Object idsRaw = body.get("ids");
            if (!(idsRaw instanceof List<?> ids)) {
                throw new IllegalArgumentException("ids is required");
            }
            List<Long> idsList = ids.stream().map(value -> ((Number) value).longValue()).toList();
            menuItemService.batchUpdateStatus(idsList, (String) body.get("status"));
            return ApiResult.success(new LinkedHashMap<>());
        });
    }

    @PostMapping("/images")
    public Map<String, Object> uploadImage(HttpServletRequest request, HttpServletResponse response,
                                           @RequestParam("file") MultipartFile file) {
        return AppApiExecutor.guard(response, () -> {
            authService.requirePermission(request, ITEM_MANAGE);
            MenuItemImageStorageService.StoredImage stored = storageService.store(file);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("imagePath", MenuItemImageUrls.PUBLIC_BASE_PATH + stored.fileName());
            return ApiResult.success(data);
        });
    }

    private MenuItemEntity normalizeImages(MenuItemEntity item) {
        item.setImagePath(MenuItemImageUrls.toPublic(item.getImagePath()));
        if (item.getImages() != null) {
            for (MenuItemImageEntity image : item.getImages()) {
                image.setImagePath(MenuItemImageUrls.toPublic(image.getImagePath()));
            }
        }
        return item;
    }
}
