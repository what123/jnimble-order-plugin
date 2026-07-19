package com.jnimble.plugin.order;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

@Configuration(proxyBeanMethods = false)
@ComponentScan(
        basePackages = "com.jnimble.plugin.order",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.jnimble\\.plugin\\.order\\.table\\..*"
        )
)
@MapperScan(basePackages = "com.jnimble.plugin.order.mapper")
public class OrderCorePluginConfiguration {
}
