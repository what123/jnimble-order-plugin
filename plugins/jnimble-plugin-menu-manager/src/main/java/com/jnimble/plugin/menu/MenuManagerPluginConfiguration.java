package com.jnimble.plugin.menu;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = "com.jnimble.plugin.menu")
@MapperScan(basePackages = "com.jnimble.plugin.menu.mapper")
public class MenuManagerPluginConfiguration {
}
