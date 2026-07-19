package com.jnimble.plugin.menu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jnimble.plugin.menu.model.entity.MenuItemEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MenuItemMapper extends BaseMapper<MenuItemEntity> {

    @Update("UPDATE menu_item SET image_path = #{imagePath} WHERE id = #{id}")
    int updateImagePath(@Param("id") Long id, @Param("imagePath") String imagePath);
}
