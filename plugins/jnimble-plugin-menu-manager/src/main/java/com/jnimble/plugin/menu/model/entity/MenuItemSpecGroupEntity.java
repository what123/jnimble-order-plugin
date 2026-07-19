package com.jnimble.plugin.menu.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import java.util.List;

@TableName("menu_item_spec_group")
public class MenuItemSpecGroupEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long itemId;
    private String name;
    private Boolean required;
    private Boolean multi;
    private Integer sortOrder;
    private Instant createdAt;
    private Instant updatedAt;

    @TableField(exist = false)
    private List<MenuItemSpecOptionEntity> options;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Boolean getRequired() { return required; }
    public void setRequired(Boolean required) { this.required = required; }
    public Boolean getMulti() { return multi; }
    public void setMulti(Boolean multi) { this.multi = multi; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public List<MenuItemSpecOptionEntity> getOptions() { return options; }
    public void setOptions(List<MenuItemSpecOptionEntity> options) { this.options = options; }
}
