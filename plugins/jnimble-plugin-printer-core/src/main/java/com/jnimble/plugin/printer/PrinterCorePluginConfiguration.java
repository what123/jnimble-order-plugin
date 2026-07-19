package com.jnimble.plugin.printer;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = "com.jnimble.plugin.printer")
@MapperScan(basePackages = "com.jnimble.plugin.printer.mapper")
public class PrinterCorePluginConfiguration {
}
