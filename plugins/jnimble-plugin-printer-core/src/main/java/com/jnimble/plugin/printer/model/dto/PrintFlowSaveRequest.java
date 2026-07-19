package com.jnimble.plugin.printer.model.dto;

import java.util.List;

public record PrintFlowSaveRequest(String billingMode, String xml, List<PrintNodeBinding> nodes) {

    public PrintFlowSaveRequest(String xml, List<PrintNodeBinding> nodes) {
        this("POSTPAID", xml, nodes);
    }

    public PrintFlowSaveRequest {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
    }
}
