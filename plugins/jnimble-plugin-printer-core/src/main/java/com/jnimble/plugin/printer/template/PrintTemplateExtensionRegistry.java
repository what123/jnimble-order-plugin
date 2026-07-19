package com.jnimble.plugin.printer.template;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jnimble.sdk.hook.RegistrationHandle;
import com.jnimble.sdk.hook.RegistrationType;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

@Component
public class PrintTemplateExtensionRegistry {

    private final ConcurrentMap<String, PrintBlockProvider> providers = new ConcurrentHashMap<>();

    public PrintTemplateExtensionRegistry(ObjectMapper objectMapper) {
        BuiltInPrintBlockProviders.create(objectMapper).forEach(this::registerBuiltIn);
    }

    public RegistrationHandle registerBlock(String pluginId, PrintBlockProvider provider) {
        if (pluginId == null || pluginId.isBlank()) {
            throw new IllegalArgumentException("Plugin ID is required");
        }
        if (provider == null || provider.descriptor() == null) {
            throw new IllegalArgumentException("Print block provider and descriptor are required");
        }
        PrintBlockDescriptor descriptor = provider.descriptor();
        if (!pluginId.equals(descriptor.provider())) {
            throw new IllegalArgumentException("Block provider must match the registering plugin ID");
        }
        registerUnique(provider);

        String registrationId = "print-block-" + UUID.randomUUID();
        AtomicBoolean active = new AtomicBoolean(true);
        return new RegistrationHandle() {
            @Override
            public void unregister() {
                if (active.compareAndSet(true, false)) {
                    providers.remove(descriptor.type(), provider);
                }
            }

            @Override
            public Optional<String> registrationId() {
                return Optional.of(registrationId);
            }

            @Override
            public RegistrationType type() {
                return RegistrationType.UNKNOWN;
            }
        };
    }

    public Optional<PrintBlockProvider> findProvider(String type) {
        return Optional.ofNullable(providers.get(type));
    }

    public List<PrintBlockDescriptor> descriptors() {
        return providers.values().stream()
                .map(PrintBlockProvider::descriptor)
                .sorted(Comparator
                        .comparingInt((PrintBlockDescriptor descriptor) ->
                                "printer-core".equals(descriptor.provider()) ? 0 : 1)
                        .thenComparingInt(descriptor -> builtInOrder(descriptor.type()))
                        .thenComparing(PrintBlockDescriptor::provider)
                        .thenComparing(PrintBlockDescriptor::type))
                .toList();
    }

    private int builtInOrder(String type) {
        return switch (type) {
            case "core.title" -> 10;
            case "core.order-details" -> 20;
            case "core.kitchen-items" -> 30;
            case "core.total-amount" -> 40;
            case "core.settlement-qr" -> 50;
            default -> 1000;
        };
    }

    private void registerBuiltIn(PrintBlockProvider provider) {
        registerUnique(provider);
    }

    private void registerUnique(PrintBlockProvider provider) {
        String type = provider.descriptor().type();
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Print block type is required");
        }
        if (providers.putIfAbsent(type, provider) != null) {
            throw new IllegalArgumentException("Print block type is already registered: " + type);
        }
    }
}
