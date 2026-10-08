package com.jnimble.plugin.app;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = "com.jnimble.plugin.app")
@MapperScan(basePackages = "com.jnimble.plugin.app.mapper")
public class AppClientPluginConfiguration {
}
