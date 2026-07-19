package com.jnimble.plugin.order.kitchen;

import com.jnimble.sdk.hook.RegistrationHandle;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

@Component
public class KitchenDispatchModeRegistry {

    private final ConcurrentMap<String, RegisteredHook> hooks = new ConcurrentHashMap<>();

    public RegistrationHandle register(String pluginId, KitchenDispatchModeHook hook, int order) {
        if (pluginId == null || pluginId.isBlank() || hook == null) {
            throw new IllegalArgumentException("Plugin ID and kitchen dispatch mode hook are required");
        }
        String registrationId = "kitchen-dispatch-" + UUID.randomUUID();
        RegisteredHook registered = new RegisteredHook(pluginId, order, hook);
        hooks.put(registrationId, registered);
        return new KitchenRegistrationHandle(registrationId, () -> hooks.remove(registrationId, registered));
    }

    public KitchenDispatchMode resolve(KitchenTicketNumberContext context) {
        for (RegisteredHook registered : orderedHooks()) {
            KitchenDispatchMode mode = registered.hook().resolve(context).orElse(null);
            if (mode != null) {
                return mode;
            }
        }
        return KitchenDispatchMode.PAPERLESS;
    }

    private List<RegisteredHook> orderedHooks() {
        return hooks.values().stream()
                .sorted(Comparator.comparingInt(RegisteredHook::order).thenComparing(RegisteredHook::pluginId))
                .toList();
    }

    private record RegisteredHook(String pluginId, int order, KitchenDispatchModeHook hook) {
    }
}
