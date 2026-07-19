package com.jnimble.plugin.printer.model.dto;

public record PrintNodeBinding(
        String nodeName,
        String printerId,
        String templateView,
        String templateId,
        String operationMode,
        String confirmationMode,
        Integer copies,
        Boolean enabled
) {

    public PrintNodeBinding(String nodeName, String printerId, String templateView, Boolean enabled) {
        this(
                nodeName,
                printerId,
                templateView,
                null,
                printerId == null || printerId.isBlank() ? "PAPERLESS" : "PRINT",
                "MANUAL",
                1,
                enabled
        );
    }

    public PrintNodeBinding(
            String nodeName,
            String printerId,
            String templateView,
            String operationMode,
            String confirmationMode,
            Boolean enabled
    ) {
        this(nodeName, printerId, templateView, null, operationMode, confirmationMode, 1, enabled);
    }

    public PrintNodeBinding(
            String nodeName,
            String printerId,
            String templateView,
            String templateId,
            String operationMode,
            String confirmationMode,
            Boolean enabled
    ) {
        this(nodeName, printerId, templateView, templateId, operationMode, confirmationMode, 1, enabled);
    }
}
