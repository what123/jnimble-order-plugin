package com.jnimble.plugin.order.payment;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import com.jnimble.sdk.hook.RegistrationHandle;
import org.springframework.stereotype.Component;

@Component
public class OrderPaymentProviderRegistry {

    private final CopyOnWriteArrayList<OrderPaymentProvider> providers;

    public OrderPaymentProviderRegistry(List<OrderPaymentProvider> providers) {
        this.providers = new CopyOnWriteArrayList<>(providers);
    }

    public RegistrationHandle register(OrderPaymentProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("Payment provider is required");
        }
        providers.addIfAbsent(provider);
        return () -> providers.remove(provider);
    }

    public OrderPaymentProvider requireProvider(String method) {
        String normalizedMethod = normalizeMethod(method);
        List<OrderPaymentProvider> matches = providers.stream()
                .filter(provider -> provider.supports(normalizedMethod))
                .toList();
        if (matches.isEmpty()) {
            throw new IllegalStateException("No payment provider is available for " + normalizedMethod);
        }
        if (matches.size() > 1) {
            throw new IllegalStateException("Multiple payment providers are registered for " + normalizedMethod);
        }
        return matches.getFirst();
    }

    public OrderPaymentResult pay(OrderPaymentRequest request) {
        return requireProvider(request.method()).pay(request);
    }

    private String normalizeMethod(String method) {
        if (method == null || method.isBlank()) {
            throw new IllegalArgumentException("Payment method is required");
        }
        return method.trim().toUpperCase(Locale.ROOT);
    }
}
