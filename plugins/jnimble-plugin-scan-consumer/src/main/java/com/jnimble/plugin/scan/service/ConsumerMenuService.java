package com.jnimble.plugin.scan.service;

import com.jnimble.plugin.menu.model.entity.CategoryEntity;
import com.jnimble.plugin.menu.model.entity.MenuItemEntity;
import com.jnimble.plugin.menu.model.entity.MenuItemImageEntity;
import com.jnimble.plugin.menu.model.entity.MenuItemSpecGroupEntity;
import com.jnimble.plugin.menu.model.entity.MenuItemSpecOptionEntity;
import com.jnimble.plugin.menu.service.CategoryService;
import com.jnimble.plugin.menu.service.MenuItemImageUrls;
import com.jnimble.plugin.menu.service.MenuItemService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ConsumerMenuService {

    private final CategoryService categoryService;
    private final MenuItemService menuItemService;

    public ConsumerMenuService(CategoryService categoryService, MenuItemService menuItemService) {
        this.categoryService = categoryService;
        this.menuItemService = menuItemService;
    }

    public Map<String, Object> getGoodsGroups(String keyword) {
        List<CategoryEntity> allCategories = categoryService.listCategories();
        List<CategoryEntity> categories = allCategories.stream()
                .filter(c -> "ENABLED".equals(c.getStatus()) || c.getStatus() == null)
                .toList();
        List<Map<String, Object>> groups = new ArrayList<>();

        for (CategoryEntity category : categories) {
            List<MenuItemEntity> items = menuItemService.listItems(category.getId(), keyword, "ENABLED");
            List<Map<String, Object>> goods = new ArrayList<>();
            for (MenuItemEntity item : items) {
                goods.add(toGoodsItem(item));
            }

            Map<String, Object> group = new java.util.HashMap<>();
            group.put("id", String.valueOf(category.getId()));
            group.put("name", category.getName());
            group.put("goods", goods);
            groups.add(group);
        }

        Map<String, Object> data = new java.util.HashMap<>();
        data.put("goodsGroup", groups);
        return data;
    }

    private Map<String, Object> toGoodsItem(MenuItemEntity item) {
        Map<String, Object> goods = new java.util.HashMap<>();
        goods.put("id", String.valueOf(item.getId()));
        goods.put("name", item.getName());

        List<Map<String, Object>> pics = new ArrayList<>();
        List<MenuItemImageEntity> images = item.getImages();
        if (images != null && !images.isEmpty()) {
            for (MenuItemImageEntity img : images) {
                Map<String, Object> pic = new java.util.HashMap<>();
                pic.put("showUrl", MenuItemImageUrls.toPublic(img.getImagePath()));
                pic.put("showThumbnail", MenuItemImageUrls.toPublic(img.getImagePath()));
                pics.add(pic);
            }
        } else if (item.getImagePath() != null && !item.getImagePath().isBlank()) {
            Map<String, Object> pic = new java.util.HashMap<>();
            pic.put("showUrl", MenuItemImageUrls.toPublic(item.getImagePath()));
            pic.put("showThumbnail", MenuItemImageUrls.toPublic(item.getImagePath()));
            pics.add(pic);
        }
        goods.put("pics", pics);

        BigDecimal price = item.getPrice() == null ? BigDecimal.ZERO : item.getPrice();
        goods.put("sellPrice", price);
        goods.put("markerPrice", price);
        goods.put("formatSellPrice", formatPrice(price));
        goods.put("formatMarkerPrice", formatPrice(price));
        goods.put("displayPrice", formatPrice(price));
        goods.put("unit", item.getUnit() == null ? "份" : item.getUnit());
        goods.put("goodsTags", List.of());
        goods.put("goodsTagsItems", List.of());
        goods.put("specGroups", toSpecGroups(item));
        goods.put("forceSelected", Boolean.TRUE.equals(item.getForceSelected()));
        return goods;
    }

    private List<Map<String, Object>> toSpecGroups(MenuItemEntity item) {
        List<Map<String, Object>> specGroups = new ArrayList<>();
        if (item.getGroups() == null) {
            return specGroups;
        }
        for (MenuItemSpecGroupEntity group : item.getGroups()) {
            Map<String, Object> groupVo = new java.util.HashMap<>();
            groupVo.put("id", group.getId());
            groupVo.put("name", group.getName());
            groupVo.put("required", Boolean.TRUE.equals(group.getRequired()));
            groupVo.put("multi", Boolean.TRUE.equals(group.getMulti()));

            List<Map<String, Object>> options = new ArrayList<>();
            if (group.getOptions() != null) {
                for (MenuItemSpecOptionEntity option : group.getOptions()) {
                    if (!"ENABLED".equals(option.getStatus())) {
                        continue;
                    }
                    Map<String, Object> optionVo = new java.util.HashMap<>();
                    optionVo.put("id", option.getId());
                    optionVo.put("name", option.getName());
                    BigDecimal adjust = option.getPriceAdjust() == null ? BigDecimal.ZERO : option.getPriceAdjust();
                    optionVo.put("priceAdjust", adjust);
                    optionVo.put("formatPriceAdjust", formatPrice(adjust));
                    options.add(optionVo);
                }
            }
            groupVo.put("options", options);
            specGroups.add(groupVo);
        }
        return specGroups;
    }

    private String formatPrice(BigDecimal price) {
        if (price == null) return "0.00";
        return price.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
