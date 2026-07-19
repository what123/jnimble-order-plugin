package com.jnimble.plugin.payment;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = "com.jnimble.plugin.payment")
@MapperScan(basePackages = "com.jnimble.plugin.payment.mapper")
public class PaymentPluginConfiguration {
}
