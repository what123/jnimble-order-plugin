package com.jnimble.plugin.menu.controller;

import com.jnimble.platform.auth.ControllerAuthorization;
import com.jnimble.plugin.menu.model.entity.CategoryEntity;
import com.jnimble.plugin.menu.model.entity.MenuItemEntity;
import com.jnimble.plugin.menu.service.CategoryService;
import com.jnimble.plugin.menu.service.MenuItemService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 菜品控制器单元测试。
 * 测试菜品列表展示、创建、更新及批量状态更新接口。
 */
@ExtendWith(MockitoExtension.class)
class MenuItemControllerTest {

    @Mock
    private MenuItemService menuItemService;

    @Mock
    private CategoryService categoryService;

    @Mock
    private ControllerAuthorization authorization;

    @InjectMocks
    private MenuItemController menuItemController;

    /**
     * 测试菜品列表页面：验证返回视图名称。
     */
    @Test
    void testListItems() {
        String viewName = menuItemController.listItems();
        assertEquals("plugin/menu-manager/admin/items", viewName);
    }

    /**
     * 测试菜品列表JSON API：验证筛选条件传递。
     */
    @Test
    void testListItemsApi() {
        Long categoryId = 1L;
        String keyword = "牛肉";
        String status = "ACTIVE";

        MenuItemEntity item = new MenuItemEntity();
        item.setId(1L);
        item.setName("红烧牛肉");

        when(menuItemService.listItems(categoryId, keyword, status)).thenReturn(List.of(item));

        @SuppressWarnings("unchecked")
        List<MenuItemEntity> result = (List<MenuItemEntity>) menuItemController.listItemsApi(categoryId, keyword, status);

        assertEquals(1, result.size());
        assertEquals("红烧牛肉", result.get(0).getName());
        verify(menuItemService).listItems(categoryId, keyword, status);
    }

    /**
     * 测试获取分类列表API（给菜品表单使用）。
     */
    @Test
    void testListCategoriesForItems() {
        CategoryEntity category = new CategoryEntity();
        category.setId(1L);
        category.setName("热菜");

        when(categoryService.listCategories()).thenReturn(List.of(category));

        @SuppressWarnings("unchecked")
        List<CategoryEntity> result = (List<CategoryEntity>) menuItemController.listCategoriesForItems();

        assertEquals(1, result.size());
        assertEquals("热菜", result.get(0).getName());
        verify(categoryService).listCategories();
    }

    /**
     * 测试创建菜品接口：验证权限检查和创建结果返回。
     */
    @Test
    @SuppressWarnings("unchecked")
    void testCreateItem() {
        // 准备测试数据
        MenuItemEntity entity = new MenuItemEntity();
        entity.setName("宫保鸡丁");
        entity.setPrice(new BigDecimal("38.00"));

        MenuItemEntity created = new MenuItemEntity();
        created.setId(1L);
        created.setName("宫保鸡丁");

        doNothing().when(authorization).requirePermission("menu-manager.item.manage");
        when(menuItemService.createItem(entity)).thenReturn(created);

        // 执行测试
        Object result = menuItemController.createItem(entity);

        // 验证结果
        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertTrue((Boolean) resultMap.get("success"));
        assertEquals(created, resultMap.get("data"));

        // 验证权限检查和Service调用
        verify(authorization).requirePermission("menu-manager.item.manage");
        verify(menuItemService).createItem(entity);
    }

    /**
     * 测试批量更新菜品状态接口：验证权限检查和批量操作执行。
     */
    @Test
    @SuppressWarnings("unchecked")
    void testBatchUpdateStatus() {
        // 准备测试数据
        Map<String, Object> body = Map.of(
                "ids", List.of(1, 2, 3),
                "status", "INACTIVE"
        );

        doNothing().when(authorization).requirePermission("menu-manager.item.manage");

        // 执行测试
        Object result = menuItemController.batchUpdateStatus(body);

        // 验证结果
        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertTrue((Boolean) resultMap.get("success"));

        // 验证权限检查和Service调用
        verify(authorization).requirePermission("menu-manager.item.manage");
        verify(menuItemService).batchUpdateStatus(List.of(1L, 2L, 3L), "INACTIVE");
    }

    /**
     * 测试更新菜品接口：验证ID设置和更新结果返回。
     */
    @Test
    @SuppressWarnings("unchecked")
    void testUpdateItem() {
        // 准备测试数据
        Long id = 1L;
        MenuItemEntity entity = new MenuItemEntity();
        entity.setName("宫保鸡丁（改名）");

        MenuItemEntity updated = new MenuItemEntity();
        updated.setId(id);
        updated.setName("宫保鸡丁（改名）");

        doNothing().when(authorization).requirePermission("menu-manager.item.manage");
        when(menuItemService.updateItem(any(MenuItemEntity.class))).thenReturn(updated);

        // 执行测试
        Object result = menuItemController.updateItem(id, entity);

        // 验证结果
        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertTrue((Boolean) resultMap.get("success"));
        assertEquals(updated, resultMap.get("data"));

        // 验证ID已设置
        assertEquals(id, entity.getId());

        // 验证权限检查和Service调用
        verify(authorization).requirePermission("menu-manager.item.manage");
        verify(menuItemService).updateItem(entity);
    }
}
