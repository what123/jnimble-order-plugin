package com.jnimble.plugin.menu.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@TableName("menu_item")
public class MenuItemEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long categoryId;
    private String name;
    private BigDecimal price;
    private String unit;
    private String imagePath;
    private String description;
    private String status;
    private Integer sortOrder;
    private Boolean forceSelected;
    private Instant createdAt;
    private Instant updatedAt;

    @TableField(exist = false)
    private List<MenuItemSpecGroupEntity> groups;

    @TableField(exist = false)
    private List<MenuItemImageEntity> images;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Boolean getForceSelected() {
        return forceSelected;
    }

    public void setForceSelected(Boolean forceSelected) {
        this.forceSelected = forceSelected;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<MenuItemSpecGroupEntity> getGroups() {
        return groups;
    }

    public void setGroups(List<MenuItemSpecGroupEntity> groups) {
        this.groups = groups;
    }

    public List<MenuItemImageEntity> getImages() {
        return images;
    }

    public void setImages(List<MenuItemImageEntity> images) {
        this.images = images;
    }
}
