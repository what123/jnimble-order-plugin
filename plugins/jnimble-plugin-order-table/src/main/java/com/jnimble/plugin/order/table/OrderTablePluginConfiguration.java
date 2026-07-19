package com.jnimble.plugin.order.table;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = "com.jnimble.plugin.order.table")
@MapperScan(basePackages = "com.jnimble.plugin.order.table.mapper")
public class OrderTablePluginConfiguration {
}
