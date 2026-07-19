package com.jnimble.plugin.order.submission;

public record OrderSubmissionCommand(
        String sourceType,
        OrderConfirmationMode confirmationMode,
        String submittedBy,
        String idempotencyKey
) {
}
