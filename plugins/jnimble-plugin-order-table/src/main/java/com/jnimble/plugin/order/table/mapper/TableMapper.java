package com.jnimble.plugin.order.table.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jnimble.plugin.order.table.model.entity.TableEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TableMapper extends BaseMapper<TableEntity> {

    @Select("SELECT * FROM ord_table WHERE id = #{id} FOR UPDATE")
    TableEntity selectByIdForUpdate(@Param("id") Long id);
}
