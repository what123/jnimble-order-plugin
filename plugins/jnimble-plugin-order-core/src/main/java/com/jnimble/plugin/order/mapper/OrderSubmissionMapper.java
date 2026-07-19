package com.jnimble.plugin.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jnimble.plugin.order.model.entity.OrderSubmissionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OrderSubmissionMapper extends BaseMapper<OrderSubmissionEntity> {

    @Select("SELECT * FROM ord_order_submission WHERE id = #{id} FOR UPDATE")
    OrderSubmissionEntity selectByIdForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM ord_order_submission WHERE idempotency_key = #{key} LIMIT 1")
    OrderSubmissionEntity selectByIdempotencyKey(@Param("key") String key);
}
