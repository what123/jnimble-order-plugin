package com.jnimble.plugin.order.kitchen;

import java.util.Optional;

@FunctionalInterface
public interface KitchenDispatchModeHook {

    Optional<KitchenDispatchMode> resolve(KitchenTicketNumberContext context);
}

