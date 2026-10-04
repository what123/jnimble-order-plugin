package com.jnimble.plugin.printer;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = "com.jnimble.plugin.printer",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.jnimble\\.plugin\\.printer\\.feie\\..*"))
@EnableScheduling
@MapperScan(basePackages = "com.jnimble.plugin.printer.mapper")
public class PrinterCorePluginConfiguration {
}
