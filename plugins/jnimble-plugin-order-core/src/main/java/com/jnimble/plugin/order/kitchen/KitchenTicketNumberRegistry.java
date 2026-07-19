package com.jnimble.plugin.order.kitchen;

import com.jnimble.sdk.hook.RegistrationHandle;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

@Component
public class KitchenTicketNumberRegistry {

    private final ConcurrentMap<String, RegisteredHook> hooks = new ConcurrentHashMap<>();

    public RegistrationHandle register(String pluginId, KitchenTicketNumberHook hook, int order) {
        if (pluginId == null || pluginId.isBlank() || hook == null) {
            throw new IllegalArgumentException("Plugin ID and kitchen ticket number hook are required");
        }
        String registrationId = "kitchen-ticket-" + UUID.randomUUID();
        RegisteredHook registered = new RegisteredHook(pluginId, order, hook);
        hooks.put(registrationId, registered);
        return new KitchenRegistrationHandle(registrationId, () -> hooks.remove(registrationId, registered));
    }

    public String resolve(KitchenTicketNumberContext context) {
        for (RegisteredHook registered : orderedHooks()) {
            String value = registered.hook().resolve(context).orElse(null);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        if (context.orderNo() != null && !context.orderNo().isBlank()) {
            return context.orderNo();
        }
        return context.orderId() == null ? "-" : String.valueOf(context.orderId());
    }

    private List<RegisteredHook> orderedHooks() {
        return hooks.values().stream()
                .sorted(Comparator.comparingInt(RegisteredHook::order).thenComparing(RegisteredHook::pluginId))
                .toList();
    }

    private record RegisteredHook(String pluginId, int order, KitchenTicketNumberHook hook) {
    }
}
