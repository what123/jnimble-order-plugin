package com.jnimble.plugin.menu.controller;

import com.jnimble.platform.auth.ControllerAuthorization;
import com.jnimble.plugin.menu.model.entity.CategoryEntity;
import com.jnimble.plugin.menu.service.CategoryService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.Model;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 分类控制器单元测试。
 * 测试分类列表展示、创建、更新及删除接口。
 */
@ExtendWith(MockitoExtension.class)
class CategoryControllerTest {

    @Mock
    private CategoryService categoryService;

    @Mock
    private ControllerAuthorization authorization;

    @InjectMocks
    private CategoryController categoryController;

    @Mock
    private Model model;

    /**
     * 测试分类列表页面：验证返回视图名称。
     */
    @Test
    void testListCategories() {
        String viewName = categoryController.listCategories(model);
        assertEquals("plugin/menu-manager/admin/categories", viewName);
    }

    /**
     * 测试分类列表JSON API：验证正确调用Service并返回数据。
     */
    @Test
    @SuppressWarnings("unchecked")
    void testListCategoriesApi() {
        CategoryEntity category = new CategoryEntity();
        category.setId(1L);
        category.setName("热菜");

        when(categoryService.listCategories()).thenReturn(List.of(category));

        Object result = categoryController.listCategoriesApi();

        assertNotNull(result);
        assertTrue(result instanceof List);
        List<CategoryEntity> list = (List<CategoryEntity>) result;
        assertEquals(1, list.size());
        assertEquals("热菜", list.get(0).getName());

        verify(categoryService).listCategories();
    }

    /**
     * 测试创建分类接口：验证权限检查和创建结果返回。
     */
    @Test
    @SuppressWarnings("unchecked")
    void testCreateCategory() {
        // 准备测试数据
        CategoryEntity entity = new CategoryEntity();
        entity.setName("饮品");

        CategoryEntity created = new CategoryEntity();
        created.setId(3L);
        created.setName("饮品");

        doNothing().when(authorization).requirePermission("menu-manager.category.manage");
        when(categoryService.createCategory(entity)).thenReturn(created);

        // 执行测试
        Object result = categoryController.createCategory(entity);

        // 验证结果
        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertTrue((Boolean) resultMap.get("success"));
        assertEquals(created, resultMap.get("data"));

        // 验证权限检查和Service调用
        verify(authorization).requirePermission("menu-manager.category.manage");
        verify(categoryService).createCategory(entity);
    }

    /**
     * 测试更新分类接口：验证ID设置和更新结果返回。
     */
    @Test
    @SuppressWarnings("unchecked")
    void testUpdateCategory() {
        // 准备测试数据
        Long id = 1L;
        CategoryEntity entity = new CategoryEntity();
        entity.setName("主食");

        CategoryEntity updated = new CategoryEntity();
        updated.setId(id);
        updated.setName("主食");

        doNothing().when(authorization).requirePermission("menu-manager.category.manage");
        when(categoryService.updateCategory(any(CategoryEntity.class))).thenReturn(updated);

        // 执行测试
        Object result = categoryController.updateCategory(id, entity);

        // 验证结果
        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertTrue((Boolean) resultMap.get("success"));
        assertEquals(updated, resultMap.get("data"));

        // 验证ID已设置
        assertEquals(id, entity.getId());

        // 验证权限检查和Service调用
        verify(authorization).requirePermission("menu-manager.category.manage");
        verify(categoryService).updateCategory(entity);
    }

    /**
     * 测试删除分类接口：验证权限检查和删除操作执行。
     */
    @Test
    @SuppressWarnings("unchecked")
    void testDeleteCategory() {
        // 准备测试数据
        Long id = 1L;

        doNothing().when(authorization).requirePermission("menu-manager.category.manage");

        // 执行测试
        Object result = categoryController.deleteCategory(id);

        // 验证结果
        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertTrue((Boolean) resultMap.get("success"));

        // 验证权限检查和Service调用
        verify(authorization).requirePermission("menu-manager.category.manage");
        verify(categoryService).deleteCategory(id);
    }
}
