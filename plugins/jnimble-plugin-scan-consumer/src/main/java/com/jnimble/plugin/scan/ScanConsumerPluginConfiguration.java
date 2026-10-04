package com.jnimble.plugin.scan;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = "com.jnimble.plugin.scan")
@MapperScan(basePackages = "com.jnimble.plugin.scan.mapper")
public class ScanConsumerPluginConfiguration {
}
