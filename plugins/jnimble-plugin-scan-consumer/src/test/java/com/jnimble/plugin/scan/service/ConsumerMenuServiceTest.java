package com.jnimble.plugin.scan.service;

import com.jnimble.plugin.menu.model.entity.CategoryEntity;
import com.jnimble.plugin.menu.model.entity.MenuItemEntity;
import com.jnimble.plugin.menu.model.entity.MenuItemImageEntity;
import com.jnimble.plugin.menu.model.entity.MenuItemSpecGroupEntity;
import com.jnimble.plugin.menu.model.entity.MenuItemSpecOptionEntity;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsumerMenuServiceTest {

    @Mock
    private CategoryService categoryService;

    @Mock
    private MenuItemService menuItemService;

    @InjectMocks
    private ConsumerMenuService consumerMenuService;

    @Test
    void exposesSpecGroupsAndPublicImageUrls() {
        CategoryEntity category = new CategoryEntity();
        category.setId(1L);
        category.setName("热菜");
        category.setStatus("ENABLED");
        when(categoryService.listCategories()).thenReturn(List.of(category));

        MenuItemEntity item = new MenuItemEntity();
        item.setId(10L);
        item.setName("宫保鸡丁");
        item.setPrice(new BigDecimal("28.00"));
        item.setStatus("ENABLED");
        MenuItemImageEntity image = new MenuItemImageEntity();
        image.setImagePath("/admin/plugins/menu-manager/items/images/00000000-0000-0000-0000-000000000001.jpg");
        item.setImages(List.of(image));

        MenuItemSpecOptionEntity option = new MenuItemSpecOptionEntity();
        option.setId(11L);
        option.setName("微辣");
        option.setPriceAdjust(new BigDecimal("1.00"));
        option.setStatus("ENABLED");
        MenuItemSpecGroupEntity group = new MenuItemSpecGroupEntity();
        group.setId(5L);
        group.setName("辣度");
        group.setRequired(true);
        group.setMulti(false);
        group.setOptions(List.of(option));
        item.setGroups(List.of(group));

        when(menuItemService.listItems(1L, "", "ENABLED")).thenReturn(List.of(item));

        Map<String, Object> data = consumerMenuService.getGoodsGroups("");

        List<Map<String, Object>> groups = (List<Map<String, Object>>) data.get("goodsGroup");
        List<Map<String, Object>> goods = (List<Map<String, Object>>) groups.getFirst().get("goods");
        Map<String, Object> goodsItem = goods.getFirst();

        List<Map<String, Object>> pics = (List<Map<String, Object>>) goodsItem.get("pics");
        assertEquals("/api/menu/images/00000000-0000-0000-0000-000000000001.jpg", pics.getFirst().get("showUrl"));

        List<Map<String, Object>> specGroups = (List<Map<String, Object>>) goodsItem.get("specGroups");
        assertEquals(1, specGroups.size());
        assertEquals("辣度", specGroups.getFirst().get("name"));
        assertEquals(true, specGroups.getFirst().get("required"));
        List<Map<String, Object>> options = (List<Map<String, Object>>) specGroups.getFirst().get("options");
        assertEquals(1, options.size());
        assertEquals("微辣", options.getFirst().get("name"));
        assertEquals("1.00", options.getFirst().get("formatPriceAdjust"));
    }
}
