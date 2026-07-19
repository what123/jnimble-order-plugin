package com.jnimble.plugin.menu.service;

import com.jnimble.platform.persistence.crud.MapperUtils;
import com.jnimble.plugin.menu.mapper.CategoryMapper;
import com.jnimble.plugin.menu.mapper.MenuItemMapper;
import com.jnimble.plugin.menu.model.entity.CategoryEntity;
import com.jnimble.plugin.menu.model.entity.MenuItemEntity;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CategoryService {

    private final CategoryMapper categoryMapper;
    private final MenuItemMapper menuItemMapper;

    public CategoryService(CategoryMapper categoryMapper, MenuItemMapper menuItemMapper) {
        this.categoryMapper = categoryMapper;
        this.menuItemMapper = menuItemMapper;
    }

    public List<CategoryEntity> listCategories() {
        return MapperUtils.selectList(categoryMapper, CategoryEntity.class,
                wrapper -> wrapper.orderByAsc("sort_order"));
    }

    public CategoryEntity getCategory(Long id) {
        return MapperUtils.getById(categoryMapper, id, "Category not found");
    }

    public CategoryEntity createCategory(CategoryEntity entity) {
        return MapperUtils.insert(categoryMapper, entity);
    }

    public CategoryEntity updateCategory(CategoryEntity entity) {
        return MapperUtils.updateById(categoryMapper, entity);
    }

    public void deleteCategory(Long id) {
        boolean hasItems = MapperUtils.existsByCondition(menuItemMapper, MenuItemEntity.class,
                wrapper -> wrapper.eq("category_id", id));
        if (hasItems) {
            throw new IllegalArgumentException("Cannot delete category with existing items");
        }
        MapperUtils.deleteById(categoryMapper, id);
    }
}
