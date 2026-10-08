package com.jnimble.plugin.scan.service;

import com.jnimble.plugin.menu.model.entity.MenuItemEntity;
import com.jnimble.plugin.menu.model.entity.MenuItemSpecGroupEntity;
import com.jnimble.plugin.menu.model.entity.MenuItemSpecOptionEntity;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class SpecSelectionResolver {

    private SpecSelectionResolver() {
    }

    public static Resolution resolve(MenuItemEntity item, String tagIds) {
        List<Long> optionIds = parseIds(tagIds);
        Set<Long> selectedIds = new LinkedHashSet<>(optionIds);
        Set<Long> consumedIds = new HashSet<>();
        List<String> descriptions = new ArrayList<>();
        BigDecimal adjustment = BigDecimal.ZERO;

        for (MenuItemSpecGroupEntity group : safeGroups(item)) {
            List<MenuItemSpecOptionEntity> selected = safeOptions(group).stream()
                    .filter(option -> "ENABLED".equals(option.getStatus()))
                    .filter(option -> selectedIds.contains(option.getId()))
                    .toList();
            if (Boolean.TRUE.equals(group.getRequired()) && selected.isEmpty()) {
                throw new IllegalArgumentException("请选择" + group.getName());
            }
            if (!Boolean.TRUE.equals(group.getMulti()) && selected.size() > 1) {
                throw new IllegalArgumentException(group.getName() + "只能选择一项");
            }
            if (!selected.isEmpty()) {
                descriptions.add(group.getName() + ": " + selected.stream()
                        .map(MenuItemSpecOptionEntity::getName)
                        .reduce((left, right) -> left + "/" + right)
                        .orElse(""));
            }
            for (MenuItemSpecOptionEntity option : selected) {
                consumedIds.add(option.getId());
                if (option.getPriceAdjust() != null) {
                    adjustment = adjustment.add(option.getPriceAdjust());
                }
            }
        }
        if (!consumedIds.equals(selectedIds)) {
            throw new IllegalArgumentException("所选规格不属于该菜品");
        }
        String description = descriptions.isEmpty() ? null : String.join("; ", descriptions);
        return new Resolution(description, adjustment, normalizedTagIds(optionIds));
    }

    private static List<Long> parseIds(String tagIds) {
        if (tagIds == null || tagIds.isBlank()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (String token : tagIds.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                ids.add(Long.parseLong(trimmed));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("规格参数无效");
            }
        }
        return ids;
    }

    private static String normalizedTagIds(List<Long> optionIds) {
        if (optionIds.isEmpty()) {
            return null;
        }
        return optionIds.stream().distinct().sorted().map(String::valueOf).reduce((l, r) -> l + "," + r).orElse(null);
    }

    private static List<MenuItemSpecGroupEntity> safeGroups(MenuItemEntity item) {
        return item.getGroups() == null ? List.of() : item.getGroups();
    }

    private static List<MenuItemSpecOptionEntity> safeOptions(MenuItemSpecGroupEntity group) {
        return group.getOptions() == null ? List.of() : group.getOptions();
    }

    public record Resolution(String description, BigDecimal priceAdjustment, String normalizedTagIds) {
    }
}
