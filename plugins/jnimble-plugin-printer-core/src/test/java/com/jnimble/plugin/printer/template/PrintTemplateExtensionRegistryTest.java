package com.jnimble.plugin.printer.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jnimble.sdk.hook.RegistrationHandle;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PrintTemplateExtensionRegistryTest {

    @Test
    void builtInBlocksShouldUseProductPaletteOrder() {
        PrintTemplateExtensionRegistry registry = new PrintTemplateExtensionRegistry(new ObjectMapper());

        assertEquals(
                java.util.List.of(
                        "core.title",
                        "core.order-details",
                        "core.kitchen-items",
                        "core.total-amount",
                        "core.settlement-qr"
                ),
                registry.descriptors().stream().map(PrintBlockDescriptor::type).toList()
        );
    }

    @Test
    void pluginBlockShouldBeRemovedWhenRegistrationIsUnregistered() {
        ObjectMapper objectMapper = new ObjectMapper();
        PrintTemplateExtensionRegistry registry = new PrintTemplateExtensionRegistry(objectMapper);
        PrintBlockProvider provider = provider(objectMapper, "member.block", "member");

        RegistrationHandle handle = registry.registerBlock("member", provider);

        assertTrue(registry.findProvider("member.block").isPresent());
        handle.unregister();
        assertFalse(registry.findProvider("member.block").isPresent());
    }

    @Test
    void duplicateBlockTypeShouldBeRejected() {
        ObjectMapper objectMapper = new ObjectMapper();
        PrintTemplateExtensionRegistry registry = new PrintTemplateExtensionRegistry(objectMapper);
        registry.registerBlock("member", provider(objectMapper, "member.block", "member"));

        assertThrows(
                IllegalArgumentException.class,
                () -> registry.registerBlock("member", provider(objectMapper, "member.block", "member"))
        );
    }

    private PrintBlockProvider provider(ObjectMapper objectMapper, String type, String pluginId) {
        return new PrintBlockProvider() {
            @Override
            public PrintBlockDescriptor descriptor() {
                return new PrintBlockDescriptor(
                        type,
                        pluginId,
                        1,
                        "会员",
                        "会员扩展块",
                        10000,
                        1000,
                        Map.of()
                );
            }

            @Override
            public JsonNode render(JsonNode config, PrintRenderContext context) {
                return objectMapper.createObjectNode().put("kind", "TEXT").put("text", "会员");
            }
        };
    }
}
