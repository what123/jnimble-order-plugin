package com.jnimble.plugin.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jnimble.plugin.app.model.entity.AppAuthTokenEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AppAuthTokenMapper extends BaseMapper<AppAuthTokenEntity> {
}
