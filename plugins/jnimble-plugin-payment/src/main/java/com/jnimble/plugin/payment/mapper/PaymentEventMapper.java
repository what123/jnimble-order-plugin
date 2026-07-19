package com.jnimble.plugin.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jnimble.plugin.payment.model.entity.PaymentEventEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PaymentEventMapper extends BaseMapper<PaymentEventEntity> {
}
