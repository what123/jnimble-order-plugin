package com.jnimble.plugin.order.kitchen;

import com.jnimble.sdk.hook.RegistrationHandle;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

@Component
public class KitchenPrintGatewayRegistry {

    private final ConcurrentMap<String, RegisteredGateway> gateways = new ConcurrentHashMap<>();

    public RegistrationHandle register(String pluginId, KitchenPrintGateway gateway) {
        if (pluginId == null || pluginId.isBlank() || gateway == null) {
            throw new IllegalArgumentException("Plugin ID and kitchen print gateway are required");
        }
        String registrationId = "kitchen-print-" + UUID.randomUUID();
        RegisteredGateway registered = new RegisteredGateway(pluginId, gateway);
        gateways.put(registrationId, registered);
        return new KitchenRegistrationHandle(registrationId, () -> gateways.remove(registrationId, registered));
    }

    public KitchenPrintResult print(KitchenPrintRequest request) {
        List<RegisteredGateway> registered = List.copyOf(gateways.values());
        if (registered.isEmpty()) {
            throw new IllegalStateException("打印插件未安装，无法创建后厨打印任务");
        }
        if (registered.size() > 1) {
            throw new IllegalStateException("检测到多个后厨打印网关，无法确定打印实现");
        }
        return registered.getFirst().gateway().print(request);
    }

    private record RegisteredGateway(String pluginId, KitchenPrintGateway gateway) {
    }
}
