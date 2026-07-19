package com.jnimble.plugin.menu.controller;

import com.jnimble.platform.auth.ControllerAuthorization;
import com.jnimble.plugin.menu.model.entity.MenuItemEntity;
import com.jnimble.plugin.menu.service.CategoryService;
import com.jnimble.plugin.menu.service.MenuItemService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/admin/plugins/menu-manager")
public class MenuItemController {

    private final MenuItemService menuItemService;
    private final CategoryService categoryService;
    private final ControllerAuthorization authorization;

    public MenuItemController(MenuItemService menuItemService, CategoryService categoryService, ControllerAuthorization authorization) {
        this.menuItemService = menuItemService;
        this.categoryService = categoryService;
        this.authorization = authorization;
    }

    @GetMapping("/items")
    public String listItems() {
        return "plugin/menu-manager/admin/items";
    }

    @GetMapping("/items/list")
    @ResponseBody
    public Object listItemsApi(@RequestParam(required = false) Long categoryId,
                               @RequestParam(required = false) String keyword,
                               @RequestParam(required = false) String status) {
        return menuItemService.listItems(categoryId, keyword, status);
    }

    @GetMapping("/categories/list-for-items")
    @ResponseBody
    public Object listCategoriesForItems() {
        return categoryService.listCategories();
    }

    @PostMapping("/items")
    @ResponseBody
    public Object createItem(@RequestBody MenuItemEntity entity) {
        authorization.requirePermission("menu-manager.item.manage");
        MenuItemEntity created = menuItemService.createItem(entity);
        return Map.of("success", true, "data", created);
    }

    @PutMapping("/items/{id}")
    @ResponseBody
    public Object updateItem(@PathVariable Long id, @RequestBody MenuItemEntity entity) {
        authorization.requirePermission("menu-manager.item.manage");
        entity.setId(id);
        MenuItemEntity updated = menuItemService.updateItem(entity);
        return Map.of("success", true, "data", updated);
    }

    @PostMapping("/items/batch-status")
    @ResponseBody
    public Object batchUpdateStatus(@RequestBody Map<String, Object> body) {
        authorization.requirePermission("menu-manager.item.manage");
        @SuppressWarnings("unchecked")
        List<Integer> idsRaw = (List<Integer>) body.get("ids");
        List<Long> ids = idsRaw.stream().map(Integer::longValue).toList();
        String status = (String) body.get("status");
        menuItemService.batchUpdateStatus(ids, status);
        return Map.of("success", true);
    }
}
