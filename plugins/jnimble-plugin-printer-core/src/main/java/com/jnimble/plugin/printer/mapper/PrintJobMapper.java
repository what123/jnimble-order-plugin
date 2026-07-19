package com.jnimble.plugin.printer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jnimble.plugin.printer.model.entity.PrintJobEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PrintJobMapper extends BaseMapper<PrintJobEntity> {
}
