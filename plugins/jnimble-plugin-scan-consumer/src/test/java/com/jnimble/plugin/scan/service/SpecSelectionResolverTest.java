package com.jnimble.plugin.scan.service;

import com.jnimble.plugin.menu.model.entity.MenuItemEntity;
import com.jnimble.plugin.menu.model.entity.MenuItemSpecGroupEntity;
import com.jnimble.plugin.menu.model.entity.MenuItemSpecOptionEntity;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SpecSelectionResolverTest {

    @Test
    void resolvesDescriptionAndAdjustment() {
        MenuItemEntity item = spicyItem();

        SpecSelectionResolver.Resolution resolution = SpecSelectionResolver.resolve(item, "11");

        assertEquals("辣度: 微辣", resolution.description());
        assertEquals(new BigDecimal("1.00"), resolution.priceAdjustment());
        assertEquals("11", resolution.normalizedTagIds());
    }

    @Test
    void rejectsMissingRequiredGroup() {
        MenuItemEntity item = spicyItem();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> SpecSelectionResolver.resolve(item, null));

        assertEquals("请选择辣度", ex.getMessage());
    }

    @Test
    void rejectsMultipleSelectionForSingleGroup() {
        MenuItemEntity item = spicyItem();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> SpecSelectionResolver.resolve(item, "11,12"));

        assertEquals("辣度只能选择一项", ex.getMessage());
    }

    @Test
    void allowsMultiGroupAndSortsTagIds() {
        MenuItemEntity item = multiItem();

        SpecSelectionResolver.Resolution resolution = SpecSelectionResolver.resolve(item, "22,21");

        assertEquals("配料: 加蛋/加肉", resolution.description());
        assertEquals(new BigDecimal("2.50"), resolution.priceAdjustment());
        assertEquals("21,22", resolution.normalizedTagIds());
    }

    @Test
    void rejectsUnknownOption() {
        MenuItemEntity item = new MenuItemEntity();
        item.setId(3L);
        item.setName("可乐");
        item.setPrice(new BigDecimal("5.00"));
        MenuItemSpecGroupEntity group = group(3L, "温度", false, false, List.of(option(31L, "常温", "0.00")));
        item.setGroups(List.of(group));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> SpecSelectionResolver.resolve(item, "99"));

        assertEquals("所选规格不属于该菜品", ex.getMessage());
    }

    @Test
    void returnsNullDescriptionWithoutSpecs() {
        MenuItemEntity item = new MenuItemEntity();
        item.setId(1L);
        item.setPrice(new BigDecimal("10.00"));

        SpecSelectionResolver.Resolution resolution = SpecSelectionResolver.resolve(item, null);

        assertNull(resolution.description());
        assertEquals(BigDecimal.ZERO, resolution.priceAdjustment());
        assertNull(resolution.normalizedTagIds());
    }

    @Test
    void ignoresDisabledOptions() {
        MenuItemSpecOptionEntity disabled = new MenuItemSpecOptionEntity();
        disabled.setId(13L);
        disabled.setName("变态辣");
        disabled.setPriceAdjust(new BigDecimal("9.00"));
        disabled.setStatus("DISABLED");
        MenuItemSpecGroupEntity group = group(4L, "辣度", false, false, List.of(disabled));
        MenuItemEntity item = new MenuItemEntity();
        item.setId(4L);
        item.setName("水煮鱼");
        item.setPrice(new BigDecimal("38.00"));
        item.setGroups(List.of(group));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> SpecSelectionResolver.resolve(item, "13"));

        assertEquals("所选规格不属于该菜品", ex.getMessage());
    }

    private MenuItemEntity spicyItem() {
        MenuItemSpecOptionEntity mild = option(11L, "微辣", "1.00");
        MenuItemSpecOptionEntity extraHot = option(12L, "特辣", "2.00");
        MenuItemSpecGroupEntity group = group(1L, "辣度", true, false, List.of(mild, extraHot));
        MenuItemEntity item = new MenuItemEntity();
        item.setId(1L);
        item.setName("宫保鸡丁");
        item.setPrice(new BigDecimal("28.00"));
        item.setGroups(List.of(group));
        return item;
    }

    private MenuItemEntity multiItem() {
        MenuItemSpecOptionEntity egg = option(21L, "加蛋", "1.00");
        MenuItemSpecOptionEntity meat = option(22L, "加肉", "1.50");
        MenuItemSpecGroupEntity group = group(2L, "配料", false, true, List.of(egg, meat));
        MenuItemEntity item = new MenuItemEntity();
        item.setId(2L);
        item.setName("牛肉面");
        item.setPrice(new BigDecimal("20.00"));
        item.setGroups(List.of(group));
        return item;
    }

    private MenuItemSpecGroupEntity group(Long id, String name, boolean required, boolean multi,
                                          List<MenuItemSpecOptionEntity> options) {
        MenuItemSpecGroupEntity group = new MenuItemSpecGroupEntity();
        group.setId(id);
        group.setName(name);
        group.setRequired(required);
        group.setMulti(multi);
        group.setOptions(options);
        return group;
    }

    private MenuItemSpecOptionEntity option(Long id, String name, String adjust) {
        MenuItemSpecOptionEntity option = new MenuItemSpecOptionEntity();
        option.setId(id);
        option.setName(name);
        option.setPriceAdjust(new BigDecimal(adjust));
        option.setStatus("ENABLED");
        return option;
    }
}
