package com.jnimble.plugin.menu.service;

import com.jnimble.platform.persistence.crud.MapperUtils;
import com.jnimble.plugin.menu.mapper.MenuItemImageMapper;
import com.jnimble.plugin.menu.mapper.MenuItemMapper;
import com.jnimble.plugin.menu.mapper.MenuItemSpecGroupMapper;
import com.jnimble.plugin.menu.mapper.MenuItemSpecOptionMapper;
import com.jnimble.plugin.menu.model.entity.MenuItemEntity;
import com.jnimble.plugin.menu.model.entity.MenuItemImageEntity;
import com.jnimble.plugin.menu.model.entity.MenuItemSpecGroupEntity;
import java.math.BigDecimal;
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
 * 菜品服务单元测试。
 * 测试菜品列表查询、详情查询、创建、更新及批量状态更新功能。
 */
@ExtendWith(MockitoExtension.class)
class MenuItemServiceTest {

    @Mock
    private MenuItemMapper menuItemMapper;

    @Mock
    private MenuItemImageMapper imageMapper;

    @Mock
    private MenuItemSpecGroupMapper specGroupMapper;

    @Mock
    private MenuItemSpecOptionMapper specOptionMapper;

    @InjectMocks
    private MenuItemService menuItemService;

    private MockedStatic<MapperUtils> mapperUtilsMock;

    @BeforeEach
    void setUp() {
        mapperUtilsMock = mockStatic(MapperUtils.class);
        mapperUtilsMock.when(() -> MapperUtils.selectList(eq(specGroupMapper), eq(MenuItemSpecGroupEntity.class), any()))
                .thenReturn(List.of());
        mapperUtilsMock.when(() -> MapperUtils.selectList(eq(imageMapper), eq(MenuItemImageEntity.class), any()))
                .thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        mapperUtilsMock.close();
    }

    /**
     * 测试菜品列表查询：验证带分类和关键字过滤的查询能正确返回结果。
     */
    @Test
    void testListItems() {
        // 准备测试数据
        Long categoryId = 1L;
        String keyword = "牛肉";
        String status = "ACTIVE";

        MenuItemEntity item = new MenuItemEntity();
        item.setId(1L);
        item.setName("红烧牛肉");
        item.setCategoryId(categoryId);
        item.setPrice(new BigDecimal("68.00"));

        mapperUtilsMock.when(() -> MapperUtils.selectList(eq(menuItemMapper), eq(MenuItemEntity.class), any()))
                .thenReturn(List.of(item));

        // 执行测试
        List<MenuItemEntity> result = menuItemService.listItems(categoryId, keyword, status);

        // 验证结果
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("红烧牛肉", result.get(0).getName());

        // 验证MapperUtils调用
        mapperUtilsMock.verify(() -> MapperUtils.selectList(eq(menuItemMapper), eq(MenuItemEntity.class), any()));
    }

    /**
     * 测试菜品列表查询：验证无过滤条件时返回全部菜品。
     */
    @Test
    void testListItemsWithoutFilters() {
        // 准备测试数据
        MenuItemEntity item1 = new MenuItemEntity();
        item1.setId(1L);
        item1.setName("红烧牛肉");

        MenuItemEntity item2 = new MenuItemEntity();
        item2.setId(2L);
        item2.setName("清蒸鲈鱼");

        mapperUtilsMock.when(() -> MapperUtils.selectList(eq(menuItemMapper), eq(MenuItemEntity.class), any()))
                .thenReturn(List.of(item1, item2));

        // 执行测试
        List<MenuItemEntity> result = menuItemService.listItems(null, null, null);

        // 验证结果
        assertNotNull(result);
        assertEquals(2, result.size());
    }

    /**
     * 测试创建菜品：验证菜品能被成功创建。
     */
    @Test
    void testCreateItem() {
        // 准备测试数据
        MenuItemEntity entity = new MenuItemEntity();
        entity.setName("宫保鸡丁");
        entity.setPrice(new BigDecimal("38.00"));
        entity.setUnit("份");
        entity.setStatus("ACTIVE");

        mapperUtilsMock.when(() -> MapperUtils.insert(eq(menuItemMapper), any(MenuItemEntity.class)))
                .thenAnswer(invocation -> {
                    MenuItemEntity e = invocation.getArgument(1);
                    e.setId(1L);
                    return e;
                });

        // 执行测试
        MenuItemEntity result = menuItemService.createItem(entity);

        // 验证结果
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("宫保鸡丁", result.getName());
        assertEquals(new BigDecimal("38.00"), result.getPrice());

        // 验证MapperUtils调用
        mapperUtilsMock.verify(() -> MapperUtils.insert(eq(menuItemMapper), any(MenuItemEntity.class)));
    }

    @Test
    void testCreateItemUsesFirstImageAsPrimaryImage() {
        MenuItemEntity entity = new MenuItemEntity();
        entity.setName("宫保鸡丁");
        MenuItemImageEntity first = new MenuItemImageEntity();
        first.setImagePath("/images/first.jpg");
        MenuItemImageEntity second = new MenuItemImageEntity();
        second.setImagePath("/images/second.jpg");
        entity.setImages(List.of(first, second));

        mapperUtilsMock.when(() -> MapperUtils.insert(eq(menuItemMapper), any(MenuItemEntity.class)))
                .thenAnswer(invocation -> {
                    MenuItemEntity item = invocation.getArgument(1);
                    item.setId(1L);
                    return item;
                });

        MenuItemEntity result = menuItemService.createItem(entity);

        assertEquals("/images/first.jpg", result.getImagePath());
        assertEquals(2, result.getImages().size());
        assertEquals(0, result.getImages().get(0).getSortOrder());
        assertEquals(1, result.getImages().get(1).getSortOrder());
        mapperUtilsMock.verify(
                () -> MapperUtils.insert(eq(imageMapper), any(MenuItemImageEntity.class)),
                times(2)
        );
    }

    /**
     * 测试获取菜品详情：验证根据ID能正确返回菜品信息。
     */
    @Test
    void testGetItem() {
        // 准备测试数据
        Long id = 1L;
        MenuItemEntity item = new MenuItemEntity();
        item.setId(id);
        item.setName("麻婆豆腐");
        item.setPrice(new BigDecimal("28.00"));
        item.setCategoryId(1L);

        mapperUtilsMock.when(() -> MapperUtils.getById(eq(menuItemMapper), eq(id), any()))
                .thenReturn(item);

        // 执行测试
        MenuItemEntity result = menuItemService.getItem(id);

        // 验证结果
        assertNotNull(result);
        assertEquals(id, result.getId());
        assertEquals("麻婆豆腐", result.getName());
        assertEquals(new BigDecimal("28.00"), result.getPrice());

        // 验证MapperUtils调用
        mapperUtilsMock.verify(() -> MapperUtils.getById(eq(menuItemMapper), eq(id), any()));
    }

    /**
     * 测试获取不存在的菜品：验证查询不存在的菜品时抛出IllegalArgumentException。
     */
    @Test
    void testGetItemNotFound() {
        // 准备测试数据
        Long id = 999L;

        mapperUtilsMock.when(() -> MapperUtils.getById(eq(menuItemMapper), eq(id), any()))
                .thenThrow(new IllegalArgumentException("Menu item not found"));

        // 执行测试并验证异常
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> menuItemService.getItem(id)
        );
        assertEquals("Menu item not found", exception.getMessage());
    }

    /**
     * 测试更新菜品：验证菜品能被成功更新。
     */
    @Test
    void testUpdateItem() {
        // 准备测试数据
        MenuItemEntity entity = new MenuItemEntity();
        entity.setId(1L);
        entity.setName("红烧排骨");
        entity.setPrice(new BigDecimal("58.00"));

        mapperUtilsMock.when(() -> MapperUtils.updateById(eq(menuItemMapper), any(MenuItemEntity.class)))
                .thenReturn(entity);

        // 执行测试
        MenuItemEntity result = menuItemService.updateItem(entity);

        // 验证结果
        assertNotNull(result);
        assertEquals("红烧排骨", result.getName());
        assertEquals(new BigDecimal("58.00"), result.getPrice());

        // 验证MapperUtils调用
        mapperUtilsMock.verify(() -> MapperUtils.updateById(eq(menuItemMapper), any(MenuItemEntity.class)));
    }

    @Test
    void testUpdateItemCanClearAllImages() {
        MenuItemEntity entity = new MenuItemEntity();
        entity.setId(1L);
        entity.setName("红烧排骨");
        entity.setImages(List.of());

        mapperUtilsMock.when(() -> MapperUtils.updateById(eq(menuItemMapper), any(MenuItemEntity.class)))
                .thenReturn(entity);

        MenuItemEntity result = menuItemService.updateItem(entity);

        assertNull(result.getImagePath());
        assertTrue(result.getImages().isEmpty());
        verify(menuItemMapper).updateImagePath(1L, null);
        mapperUtilsMock.verify(() -> MapperUtils.deleteByCondition(
                eq(imageMapper), eq(MenuItemImageEntity.class), any()));
    }

    /**
     * 测试批量更新菜品状态：验证能批量更新多个菜品的状态。
     */
    @Test
    void testBatchUpdateStatus() {
        // 准备测试数据
        List<Long> ids = List.of(1L, 2L, 3L);
        String status = "INACTIVE";

        mapperUtilsMock.when(() -> MapperUtils.updateByCondition(eq(menuItemMapper), any(MenuItemEntity.class), eq(MenuItemEntity.class), any()))
                .thenReturn(3);

        // 执行测试
        menuItemService.batchUpdateStatus(ids, status);

        // 验证MapperUtils调用
        mapperUtilsMock.verify(() -> MapperUtils.updateByCondition(eq(menuItemMapper), any(MenuItemEntity.class), eq(MenuItemEntity.class), any()));
    }
}
