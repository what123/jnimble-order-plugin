package com.jnimble.plugin.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jnimble.plugin.order.model.entity.OrderEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OrderMapper extends BaseMapper<OrderEntity> {

    @Select("SELECT * FROM ord_order WHERE id = #{id} FOR UPDATE")
    OrderEntity selectByIdForUpdate(@Param("id") Long id);
}
