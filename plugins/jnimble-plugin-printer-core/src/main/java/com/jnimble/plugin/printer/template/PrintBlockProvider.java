package com.jnimble.plugin.printer.template;

import com.fasterxml.jackson.databind.JsonNode;
import com.jnimble.plugin.printer.model.dto.PrintTemplateViolation;
import java.util.List;

public interface PrintBlockProvider {

    PrintBlockDescriptor descriptor();

    default List<PrintTemplateViolation> validate(JsonNode config) {
        return List.of();
    }

    JsonNode render(JsonNode config, PrintRenderContext context);
}
