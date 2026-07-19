package com.jnimble.plugin.menu.service;

import com.jnimble.platform.persistence.crud.MapperUtils;
import com.jnimble.plugin.menu.mapper.CategoryMapper;
import com.jnimble.plugin.menu.mapper.MenuItemMapper;
import com.jnimble.plugin.menu.model.entity.CategoryEntity;
import com.jnimble.plugin.menu.model.entity.MenuItemEntity;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 分类服务单元测试。
 * 测试分类列表查询、创建、更新及删除功能。
 */
@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryMapper categoryMapper;

    @Mock
    private MenuItemMapper menuItemMapper;

    @InjectMocks
    private CategoryService categoryService;

    private MockedStatic<MapperUtils> mapperUtilsMock;

    @BeforeEach
    void setUp() {
        mapperUtilsMock = mockStatic(MapperUtils.class);
    }

    @AfterEach
    void tearDown() {
        mapperUtilsMock.close();
    }

    /**
     * 测试分类列表查询：验证返回所有分类且按排序字段升序排列。
     */
    @Test
    void testListCategories() {
        // 准备测试数据
        CategoryEntity category1 = new CategoryEntity();
        category1.setId(1L);
        category1.setName("热菜");
        category1.setSortOrder(1);

        CategoryEntity category2 = new CategoryEntity();
        category2.setId(2L);
        category2.setName("凉菜");
        category2.setSortOrder(2);

        mapperUtilsMock.when(() -> MapperUtils.selectList(eq(categoryMapper), eq(CategoryEntity.class), any()))
                .thenReturn(List.of(category1, category2));

        // 执行测试
        List<CategoryEntity> result = categoryService.listCategories();

        // 验证结果
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("热菜", result.get(0).getName());
        assertEquals("凉菜", result.get(1).getName());

        // 验证MapperUtils调用
        mapperUtilsMock.verify(() -> MapperUtils.selectList(eq(categoryMapper), eq(CategoryEntity.class), any()));
    }

    /**
     * 测试获取分类详情：验证根据ID能正确返回分类信息。
     */
    @Test
    void testGetCategory() {
        // 准备测试数据
        Long id = 1L;
        CategoryEntity category = new CategoryEntity();
        category.setId(id);
        category.setName("热菜");
        category.setSortOrder(1);
        category.setStatus("ACTIVE");

        mapperUtilsMock.when(() -> MapperUtils.getById(eq(categoryMapper), eq(id), any()))
                .thenReturn(category);

        // 执行测试
        CategoryEntity result = categoryService.getCategory(id);

        // 验证结果
        assertNotNull(result);
        assertEquals(id, result.getId());
        assertEquals("热菜", result.getName());
        assertEquals(1, result.getSortOrder());

        // 验证MapperUtils调用
        mapperUtilsMock.verify(() -> MapperUtils.getById(eq(categoryMapper), eq(id), any()));
    }

    /**
     * 测试获取不存在的分类：验证查询不存在的分类时抛出IllegalArgumentException。
     */
    @Test
    void testGetCategoryNotFound() {
        // 准备测试数据
        Long id = 999L;

        mapperUtilsMock.when(() -> MapperUtils.getById(eq(categoryMapper), eq(id), any()))
                .thenThrow(new IllegalArgumentException("Category not found"));

        // 执行测试并验证异常
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> categoryService.getCategory(id)
        );
        assertEquals("Category not found", exception.getMessage());
    }

    /**
     * 测试创建分类：验证分类能被成功创建并返回完整对象。
     */
    @Test
    void testCreateCategory() {
        // 准备测试数据
        CategoryEntity entity = new CategoryEntity();
        entity.setName("饮品");
        entity.setSortOrder(10);
        entity.setStatus("ACTIVE");

        mapperUtilsMock.when(() -> MapperUtils.insert(eq(categoryMapper), any(CategoryEntity.class)))
                .thenAnswer(invocation -> {
                    CategoryEntity e = invocation.getArgument(1);
                    e.setId(3L);
                    return e;
                });

        // 执行测试
        CategoryEntity result = categoryService.createCategory(entity);

        // 验证结果
        assertNotNull(result);
        assertEquals(3L, result.getId());
        assertEquals("饮品", result.getName());

        // 验证MapperUtils调用
        mapperUtilsMock.verify(() -> MapperUtils.insert(eq(categoryMapper), any(CategoryEntity.class)));
    }

    /**
     * 测试更新分类：验证分类能被成功更新。
     */
    @Test
    void testUpdateCategory() {
        // 准备测试数据
        CategoryEntity entity = new CategoryEntity();
        entity.setId(1L);
        entity.setName("主食");

        mapperUtilsMock.when(() -> MapperUtils.updateById(eq(categoryMapper), any(CategoryEntity.class)))
                .thenReturn(entity);

        // 执行测试
        CategoryEntity result = categoryService.updateCategory(entity);

        // 验证结果
        assertNotNull(result);
        assertEquals("主食", result.getName());

        // 验证MapperUtils调用
        mapperUtilsMock.verify(() -> MapperUtils.updateById(eq(categoryMapper), any(CategoryEntity.class)));
    }

    /**
     * 测试删除无菜品的分类：验证空分类能被成功删除。
     */
    @Test
    void testDeleteCategory() {
        // 准备测试数据
        Long categoryId = 1L;

        // 模拟分类下无菜品
        mapperUtilsMock.when(() -> MapperUtils.existsByCondition(eq(menuItemMapper), eq(MenuItemEntity.class), any()))
                .thenReturn(false);
        mapperUtilsMock.when(() -> MapperUtils.deleteById(eq(categoryMapper), eq(categoryId)))
                .thenReturn(1);

        // 执行测试
        categoryService.deleteCategory(categoryId);

        // 验证MapperUtils调用
        mapperUtilsMock.verify(() -> MapperUtils.existsByCondition(eq(menuItemMapper), eq(MenuItemEntity.class), any()));
        mapperUtilsMock.verify(() -> MapperUtils.deleteById(eq(categoryMapper), eq(categoryId)));
    }

    /**
     * 测试删除有菜品的分类：验证包含菜品的分类不能被删除。
     */
    @Test
    void testDeleteCategoryWithItemsThrows() {
        // 准备测试数据
        Long categoryId = 1L;

        // 模拟分类下有菜品
        mapperUtilsMock.when(() -> MapperUtils.existsByCondition(eq(menuItemMapper), eq(MenuItemEntity.class), any()))
                .thenReturn(true);

        // 执行测试并验证异常
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> categoryService.deleteCategory(categoryId)
        );
        assertEquals("Cannot delete category with existing items", exception.getMessage());

        // 验证不应调用删除
        mapperUtilsMock.verify(() -> MapperUtils.deleteById(eq(categoryMapper), eq(categoryId)), never());
    }
}
