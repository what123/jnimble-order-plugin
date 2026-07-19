package com.jnimble.plugin.printer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jnimble.plugin.printer.model.entity.PrinterEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PrinterMapper extends BaseMapper<PrinterEntity> {
}
