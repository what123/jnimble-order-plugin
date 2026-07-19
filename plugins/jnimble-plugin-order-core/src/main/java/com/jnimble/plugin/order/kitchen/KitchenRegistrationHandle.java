package com.jnimble.plugin.order.kitchen;

import com.jnimble.sdk.hook.RegistrationHandle;
import com.jnimble.sdk.hook.RegistrationType;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

final class KitchenRegistrationHandle implements RegistrationHandle {

    private final String registrationId;
    private final Runnable unregisterAction;
    private final AtomicBoolean active = new AtomicBoolean(true);

    KitchenRegistrationHandle(String registrationId, Runnable unregisterAction) {
        this.registrationId = registrationId;
        this.unregisterAction = unregisterAction;
    }

    @Override
    public void unregister() {
        if (active.compareAndSet(true, false)) {
            unregisterAction.run();
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
}

