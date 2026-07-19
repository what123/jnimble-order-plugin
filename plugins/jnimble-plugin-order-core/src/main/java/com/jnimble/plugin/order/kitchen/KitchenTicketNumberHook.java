package com.jnimble.plugin.order.kitchen;

import java.util.Optional;

@FunctionalInterface
public interface KitchenTicketNumberHook {

    Optional<String> resolve(KitchenTicketNumberContext context);
}

