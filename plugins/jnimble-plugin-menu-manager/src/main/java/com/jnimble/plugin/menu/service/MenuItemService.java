package com.jnimble.plugin.menu.service;

import com.jnimble.platform.persistence.crud.MapperUtils;
import com.jnimble.plugin.menu.mapper.MenuItemImageMapper;
import com.jnimble.plugin.menu.mapper.MenuItemMapper;
import com.jnimble.plugin.menu.mapper.MenuItemSpecGroupMapper;
import com.jnimble.plugin.menu.mapper.MenuItemSpecOptionMapper;
import com.jnimble.plugin.menu.model.entity.MenuItemEntity;
import com.jnimble.plugin.menu.model.entity.MenuItemImageEntity;
import com.jnimble.plugin.menu.model.entity.MenuItemSpecGroupEntity;
import com.jnimble.plugin.menu.model.entity.MenuItemSpecOptionEntity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MenuItemService {

    static final int MAX_IMAGE_COUNT = 10;

    private final MenuItemMapper menuItemMapper;
    private final MenuItemImageMapper imageMapper;
    private final MenuItemSpecGroupMapper specGroupMapper;
    private final MenuItemSpecOptionMapper specOptionMapper;

    public MenuItemService(MenuItemMapper menuItemMapper,
                           MenuItemImageMapper imageMapper,
                           MenuItemSpecGroupMapper specGroupMapper,
                           MenuItemSpecOptionMapper specOptionMapper) {
        this.menuItemMapper = menuItemMapper;
        this.imageMapper = imageMapper;
        this.specGroupMapper = specGroupMapper;
        this.specOptionMapper = specOptionMapper;
    }

    public List<MenuItemEntity> listItems(Long categoryId, String keyword, String status) {
        List<MenuItemEntity> items = MapperUtils.selectList(menuItemMapper, MenuItemEntity.class, wrapper -> {
            if (categoryId != null) {
                wrapper.eq("category_id", categoryId);
            }
            if (keyword != null && !keyword.isBlank()) {
                wrapper.like("name", keyword);
            }
            if (status != null && !status.isBlank()) {
                wrapper.eq("status", status);
            }
            wrapper.orderByAsc("sort_order");
        });
        attachGroups(items);
        attachImages(items);
        return items;
    }

    public List<MenuItemEntity> getForceSelectedItems(Long storeId) {
        return MapperUtils.selectList(menuItemMapper, MenuItemEntity.class, wrapper -> {
            wrapper.eq("force_selected", true);
            wrapper.eq("status", "ENABLED");
            wrapper.orderByAsc("sort_order");
        });
    }

    public MenuItemEntity getItem(Long id) {
        MenuItemEntity item = MapperUtils.getById(menuItemMapper, id, "Menu item not found");
        attachGroups(List.of(item));
        attachImages(List.of(item));
        return item;
    }

    @Transactional
    public MenuItemEntity createItem(MenuItemEntity entity) {
        List<MenuItemSpecGroupEntity> groups = entity.getGroups();
        List<MenuItemImageEntity> images = normalizeImages(entity.getImages());
        if (images == null) {
            images = imageListFromLegacyPath(entity.getImagePath());
        }
        entity.setImagePath(primaryImagePath(images));
        entity.setGroups(null);
        entity.setImages(null);
        MenuItemEntity created = MapperUtils.insert(menuItemMapper, entity);
        saveGroups(created.getId(), groups);
        saveImages(created.getId(), images);
        created.setGroups(groups);
        created.setImages(images);
        return created;
    }

    @Transactional
    public MenuItemEntity updateItem(MenuItemEntity entity) {
        List<MenuItemSpecGroupEntity> groups = entity.getGroups();
        List<MenuItemImageEntity> images = normalizeImages(entity.getImages());
        if (images != null) {
            entity.setImagePath(primaryImagePath(images));
        }
        entity.setGroups(null);
        entity.setImages(null);
        MenuItemEntity updated = MapperUtils.updateById(menuItemMapper, entity);
        deleteGroupsByItem(entity.getId());
        saveGroups(entity.getId(), groups);
        if (images != null) {
            menuItemMapper.updateImagePath(entity.getId(), primaryImagePath(images));
            deleteImagesByItem(entity.getId());
            saveImages(entity.getId(), images);
        }
        updated.setGroups(groups);
        updated.setImages(images);
        return updated;
    }

    public void batchUpdateStatus(List<Long> ids, String status) {
        MenuItemEntity update = new MenuItemEntity();
        update.setStatus(status);
        MapperUtils.updateByCondition(menuItemMapper, update, MenuItemEntity.class,
                wrapper -> wrapper.in("id", ids));
    }

    private void saveGroups(Long itemId, List<MenuItemSpecGroupEntity> groups) {
        if (groups == null || groups.isEmpty()) return;
        for (MenuItemSpecGroupEntity group : groups) {
            List<MenuItemSpecOptionEntity> options = group.getOptions();
            group.setId(null);
            group.setItemId(itemId);
            group.setOptions(null);
            MapperUtils.insert(specGroupMapper, group);
            saveOptions(group.getId(), options);
            group.setOptions(options);
        }
    }

    private void saveOptions(Long groupId, List<MenuItemSpecOptionEntity> options) {
        if (options == null || options.isEmpty()) return;
        for (MenuItemSpecOptionEntity opt : options) {
            opt.setId(null);
            opt.setGroupId(groupId);
            MapperUtils.insert(specOptionMapper, opt);
        }
    }

    private void saveImages(Long itemId, List<MenuItemImageEntity> images) {
        if (images == null || images.isEmpty()) return;
        for (int index = 0; index < images.size(); index++) {
            MenuItemImageEntity image = images.get(index);
            image.setId(null);
            image.setItemId(itemId);
            image.setSortOrder(index);
            MapperUtils.insert(imageMapper, image);
        }
    }

    private void deleteGroupsByItem(Long itemId) {
        List<MenuItemSpecGroupEntity> groups = MapperUtils.selectList(
                specGroupMapper, MenuItemSpecGroupEntity.class,
                wrapper -> wrapper.eq("item_id", itemId));
        if (groups.isEmpty()) return;
        List<Long> groupIds = groups.stream().map(MenuItemSpecGroupEntity::getId).toList();
        MapperUtils.deleteByCondition(specOptionMapper, MenuItemSpecOptionEntity.class,
                wrapper -> wrapper.in("group_id", groupIds));
        MapperUtils.deleteByCondition(specGroupMapper, MenuItemSpecGroupEntity.class,
                wrapper -> wrapper.eq("item_id", itemId));
    }

    private void deleteImagesByItem(Long itemId) {
        MapperUtils.deleteByCondition(imageMapper, MenuItemImageEntity.class,
                wrapper -> wrapper.eq("item_id", itemId));
    }

    private void attachGroups(List<MenuItemEntity> items) {
        if (items.isEmpty()) return;
        List<Long> itemIds = items.stream().map(MenuItemEntity::getId).toList();
        List<MenuItemSpecGroupEntity> groups = MapperUtils.selectList(
                specGroupMapper, MenuItemSpecGroupEntity.class,
                wrapper -> wrapper.in("item_id", itemIds).orderByAsc("sort_order"));
        if (groups.isEmpty()) return;
        List<Long> groupIds = groups.stream().map(MenuItemSpecGroupEntity::getId).toList();
        List<MenuItemSpecOptionEntity> allOptions = MapperUtils.selectList(
                specOptionMapper, MenuItemSpecOptionEntity.class,
                wrapper -> wrapper.in("group_id", groupIds).orderByAsc("sort_order"));
        Map<Long, List<MenuItemSpecOptionEntity>> optionMap = allOptions.stream()
                .collect(Collectors.groupingBy(MenuItemSpecOptionEntity::getGroupId));
        for (MenuItemSpecGroupEntity group : groups) {
            group.setOptions(optionMap.getOrDefault(group.getId(), List.of()));
        }
        Map<Long, List<MenuItemSpecGroupEntity>> groupMap = groups.stream()
                .collect(Collectors.groupingBy(MenuItemSpecGroupEntity::getItemId));
        for (MenuItemEntity item : items) {
            item.setGroups(groupMap.getOrDefault(item.getId(), List.of()));
        }
    }

    private void attachImages(List<MenuItemEntity> items) {
        if (items.isEmpty()) return;
        List<Long> itemIds = items.stream().map(MenuItemEntity::getId).toList();
        List<MenuItemImageEntity> images = MapperUtils.selectList(
                imageMapper, MenuItemImageEntity.class,
                wrapper -> wrapper.in("item_id", itemIds)
                        .orderByAsc("item_id", "sort_order", "id"));
        Map<Long, List<MenuItemImageEntity>> imageMap = images.stream()
                .collect(Collectors.groupingBy(MenuItemImageEntity::getItemId));
        for (MenuItemEntity item : items) {
            List<MenuItemImageEntity> itemImages = new ArrayList<>(
                    imageMap.getOrDefault(item.getId(), List.of()));
            if (itemImages.isEmpty()) {
                itemImages.addAll(imageListFromLegacyPath(item.getImagePath()));
            }
            item.setImages(itemImages);
        }
    }

    private List<MenuItemImageEntity> normalizeImages(List<MenuItemImageEntity> images) {
        if (images == null) return null;
        Map<String, MenuItemImageEntity> uniqueImages = new LinkedHashMap<>();
        for (MenuItemImageEntity image : images) {
            if (image == null || image.getImagePath() == null || image.getImagePath().isBlank()) {
                continue;
            }
            String imagePath = image.getImagePath().trim();
            if (imagePath.length() > 500) {
                throw new IllegalArgumentException("Image path cannot exceed 500 characters");
            }
            MenuItemImageEntity normalized = new MenuItemImageEntity();
            normalized.setImagePath(imagePath);
            uniqueImages.putIfAbsent(imagePath, normalized);
        }
        if (uniqueImages.size() > MAX_IMAGE_COUNT) {
            throw new IllegalArgumentException("A menu item can have at most " + MAX_IMAGE_COUNT + " images");
        }
        List<MenuItemImageEntity> result = new ArrayList<>(uniqueImages.values());
        for (int index = 0; index < result.size(); index++) {
            result.get(index).setSortOrder(index);
        }
        return result;
    }

    private List<MenuItemImageEntity> imageListFromLegacyPath(String imagePath) {
        if (imagePath == null || imagePath.isBlank()) return new ArrayList<>();
        MenuItemImageEntity image = new MenuItemImageEntity();
        image.setImagePath(imagePath.trim());
        image.setSortOrder(0);
        return new ArrayList<>(List.of(image));
    }

    private String primaryImagePath(List<MenuItemImageEntity> images) {
        return images == null || images.isEmpty() ? null : images.get(0).getImagePath();
    }
}
