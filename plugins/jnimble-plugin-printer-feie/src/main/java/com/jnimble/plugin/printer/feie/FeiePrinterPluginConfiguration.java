package com.jnimble.plugin.printer.feie;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * 飞鹅打印插件配置类。
 *
 * <p>确保 feie 包下的 controller / service / 等组件被 Spring 扫描注册。
 * printer-core 的 ComponentScan 范围 {@code com.jnimble.plugin.printer} 也覆盖本包,
 * 本类作为兜底,确保独立部署时 feie 插件自身能加载组件。</p>
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = "com.jnimble.plugin.printer.feie")
public class FeiePrinterPluginConfiguration {
}
