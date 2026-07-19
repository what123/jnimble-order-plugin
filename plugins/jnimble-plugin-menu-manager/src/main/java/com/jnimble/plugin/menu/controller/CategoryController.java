package com.jnimble.plugin.menu.controller;

import com.jnimble.platform.auth.ControllerAuthorization;
import com.jnimble.plugin.menu.model.entity.CategoryEntity;
import com.jnimble.plugin.menu.service.CategoryService;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/admin/plugins/menu-manager")
public class CategoryController {

    private final CategoryService categoryService;
    private final ControllerAuthorization authorization;

    public CategoryController(CategoryService categoryService, ControllerAuthorization authorization) {
        this.categoryService = categoryService;
        this.authorization = authorization;
    }

    @GetMapping("/categories")
    public String listCategories(Model model) {
        return "plugin/menu-manager/admin/categories";
    }

    @GetMapping("/categories/list")
    @ResponseBody
    public Object listCategoriesApi() {
        return categoryService.listCategories();
    }

    @PostMapping("/categories")
    @ResponseBody
    public Object createCategory(@RequestBody CategoryEntity entity) {
        authorization.requirePermission("menu-manager.category.manage");
        CategoryEntity created = categoryService.createCategory(entity);
        return Map.of("success", true, "data", created);
    }

    @PutMapping("/categories/{id}")
    @ResponseBody
    public Object updateCategory(@PathVariable Long id, @RequestBody CategoryEntity entity) {
        authorization.requirePermission("menu-manager.category.manage");
        entity.setId(id);
        CategoryEntity updated = categoryService.updateCategory(entity);
        return Map.of("success", true, "data", updated);
    }

    @DeleteMapping("/categories/{id}")
    @ResponseBody
    public Object deleteCategory(@PathVariable Long id) {
        authorization.requirePermission("menu-manager.category.manage");
        categoryService.deleteCategory(id);
        return Map.of("success", true);
    }
}
