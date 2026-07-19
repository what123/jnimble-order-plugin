package com.jnimble.plugin.printer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jnimble.plugin.printer.mapper.PrintFlowMapper;
import com.jnimble.plugin.printer.model.dto.PrintFlowSaveRequest;
import com.jnimble.plugin.printer.model.dto.PrintNodeBinding;
import com.jnimble.plugin.printer.model.entity.PrintFlowEntity;
import com.jnimble.plugin.printer.model.entity.PrintNodeEntity;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PrintFlowServiceTest {

    private static final List<String> FIXED_NODE_NAMES = List.of(
            "order.confirmed",
            "order.kitchen.confirmed",
            "order.served",
            "order.settled"
    );
    private static final String VALID_FLOW_XML = fixedFlowXml("POSTPAID");
    private static final String VALID_PREPAID_FLOW_XML = fixedFlowXml("PREPAID");

    private PrintFlowMapper printFlowMapper;
    private PrintNodeService printNodeService;
    private PrintTemplateService printTemplateService;
    private PrintFlowService printFlowService;

    @BeforeEach
    void setUp() {
        printFlowMapper = mock(PrintFlowMapper.class);
        printNodeService = mock(PrintNodeService.class);
        printTemplateService = mock(PrintTemplateService.class);
        printFlowService = new PrintFlowService(printFlowMapper, printNodeService, printTemplateService);
    }

    @Test
    void getFlowXmlShouldReturnNullWhenFlowDoesNotExist() {
        when(printFlowMapper.selectById("default")).thenReturn(null);

        assertNull(printFlowService.getFlowXml());
    }

    @Test
    void getFlowXmlShouldReturnSavedDefinition() {
        PrintFlowEntity entity = new PrintFlowEntity();
        entity.setFlowXml(VALID_FLOW_XML);
        when(printFlowMapper.selectById("default")).thenReturn(entity);

        assertEquals(VALID_FLOW_XML, printFlowService.getFlowXml());
    }

    @Test
    void getBillingModeShouldDefaultToPostpaid() {
        when(printFlowMapper.selectById("default")).thenReturn(null);

        assertEquals("POSTPAID", printFlowService.getBillingMode());
    }

    @Test
    void getBillingModeShouldReturnSavedMode() {
        PrintFlowEntity entity = new PrintFlowEntity();
        entity.setBillingMode("PREPAID");
        when(printFlowMapper.selectById("default")).thenReturn(entity);

        assertEquals("PREPAID", printFlowService.getBillingMode());
    }

    @Test
    void saveFlowShouldInsertDefinitionAndUpdateRegisteredNode() {
        List<PrintNodeEntity> nodes = createFixedNodes();
        PrintNodeEntity node = findNode(nodes, "order.confirmed");
        PrintNodeBinding binding = new PrintNodeBinding(
                "order.confirmed",
                "printer-1",
                "plugin/printer-core/print/kitchen",
                true
        );
        when(printNodeService.listNodes()).thenReturn(nodes);
        when(printFlowMapper.selectById("default")).thenReturn(null);

        printFlowService.saveFlow(new PrintFlowSaveRequest(VALID_FLOW_XML, bindingsWith(binding)));

        ArgumentCaptor<PrintFlowEntity> flowCaptor = ArgumentCaptor.forClass(PrintFlowEntity.class);
        verify(printFlowMapper).insert(flowCaptor.capture());
        assertEquals("default", flowCaptor.getValue().getId());
        assertEquals("POSTPAID", flowCaptor.getValue().getBillingMode());
        assertEquals(VALID_FLOW_XML, flowCaptor.getValue().getFlowXml());
        assertEquals("printer-1", node.getPrinterId());
        assertEquals("plugin/printer-core/print/kitchen", node.getTemplateView());
        assertEquals("PRINT", node.getOperationMode());
        assertEquals("MANUAL", node.getConfirmationMode());
        assertEquals(1, node.getCopies());
        assertTrue(node.getEnabled());
        verify(printNodeService).updateNode(node);
    }

    @Test
    void saveFlowShouldUpdateExistingDefinition() {
        PrintFlowEntity entity = new PrintFlowEntity();
        entity.setId("default");
        entity.setFlowXml("old");
        when(printFlowMapper.selectById("default")).thenReturn(entity);
        when(printFlowMapper.updateById(entity)).thenReturn(1);
        when(printNodeService.listNodes()).thenReturn(createFixedNodes());

        printFlowService.saveFlow(new PrintFlowSaveRequest(VALID_FLOW_XML, paperlessBindings()));

        verify(printFlowMapper).updateById(entity);
        assertEquals(VALID_FLOW_XML, entity.getFlowXml());
        assertEquals("POSTPAID", entity.getBillingMode());
        verify(printFlowMapper, never()).insert(any(PrintFlowEntity.class));
    }

    @Test
    void saveFlowShouldPersistPrepaidPaperlessAutomaticNode() {
        List<PrintNodeEntity> nodes = createFixedNodes();
        PrintNodeEntity node = findNode(nodes, "order.kitchen.confirmed");
        PrintNodeBinding binding = new PrintNodeBinding(
                "order.kitchen.confirmed",
                null,
                null,
                "PAPERLESS",
                "AUTO",
                true
        );
        when(printNodeService.listNodes()).thenReturn(nodes);
        when(printFlowMapper.selectById("default")).thenReturn(null);

        printFlowService.saveFlow(new PrintFlowSaveRequest(
                "PREPAID",
                VALID_PREPAID_FLOW_XML,
                bindingsWith(binding)
        ));

        ArgumentCaptor<PrintFlowEntity> flowCaptor = ArgumentCaptor.forClass(PrintFlowEntity.class);
        verify(printFlowMapper).insert(flowCaptor.capture());
        assertEquals("PREPAID", flowCaptor.getValue().getBillingMode());
        assertNull(node.getPrinterId());
        assertNull(node.getTemplateView());
        assertEquals("PAPERLESS", node.getOperationMode());
        assertEquals("AUTO", node.getConfirmationMode());
        assertEquals(1, node.getCopies());
        verify(printNodeService).updateNode(node);
    }

    @Test
    void saveFlowShouldPersistPublishedVisualTemplateId() {
        List<PrintNodeEntity> nodes = createFixedNodes();
        PrintNodeEntity node = findNode(nodes, "order.settled");
        PrintNodeBinding binding = new PrintNodeBinding(
                "order.settled",
                "printer-1",
                null,
                "template-1",
                "PRINT",
                "MANUAL",
                true
        );
        when(printNodeService.listNodes()).thenReturn(nodes);
        when(printFlowMapper.selectById("default")).thenReturn(null);
        when(printTemplateService.isPublishedTemplate("template-1")).thenReturn(true);

        printFlowService.saveFlow(new PrintFlowSaveRequest(VALID_FLOW_XML, bindingsWith(binding)));

        assertEquals("template-1", node.getTemplateId());
        verify(printTemplateService).isPublishedTemplate("template-1");
        verify(printNodeService).updateNode(node);
    }

    @Test
    void saveFlowShouldRejectPrintModeWithoutPrinter() {
        List<PrintNodeEntity> nodes = createFixedNodes();
        PrintNodeBinding binding = new PrintNodeBinding(
                "order.served",
                null,
                null,
                "PRINT",
                "MANUAL",
                true
        );
        when(printNodeService.listNodes()).thenReturn(nodes);

        assertThrows(
                IllegalArgumentException.class,
                () -> printFlowService.saveFlow(new PrintFlowSaveRequest(VALID_FLOW_XML, bindingsWith(binding)))
        );

        verify(printFlowMapper, never()).insert(any(PrintFlowEntity.class));
        verify(printNodeService, never()).updateNode(any(PrintNodeEntity.class));
    }

    @Test
    void saveFlowShouldRejectInvalidXmlBeforeWriting() {
        PrintFlowSaveRequest request = new PrintFlowSaveRequest("<not-bpmn />", List.of());

        assertThrows(IllegalArgumentException.class, () -> printFlowService.saveFlow(request));

        verify(printFlowMapper, never()).insert(any(PrintFlowEntity.class));
        verify(printFlowMapper, never()).updateById(any(PrintFlowEntity.class));
    }

    @Test
    void saveFlowShouldRejectUnknownNodeBeforeWriting() {
        PrintNodeBinding binding = new PrintNodeBinding("order.unknown", null, null, true);
        when(printNodeService.listNodes()).thenReturn(createFixedNodes());

        assertThrows(
                IllegalArgumentException.class,
                () -> printFlowService.saveFlow(new PrintFlowSaveRequest(VALID_FLOW_XML, List.of(binding)))
        );

        verify(printFlowMapper, never()).insert(any(PrintFlowEntity.class));
        verify(printFlowMapper, never()).updateById(any(PrintFlowEntity.class));
    }

    @Test
    void saveFlowShouldPersistConfiguredPrintCopies() {
        List<PrintNodeEntity> nodes = createFixedNodes();
        PrintNodeEntity node = findNode(nodes, "order.confirmed");
        PrintNodeBinding binding = new PrintNodeBinding(
                "order.confirmed",
                "printer-1",
                null,
                null,
                "PRINT",
                "MANUAL",
                3,
                true
        );
        when(printNodeService.listNodes()).thenReturn(nodes);
        when(printFlowMapper.selectById("default")).thenReturn(null);

        printFlowService.saveFlow(new PrintFlowSaveRequest(VALID_FLOW_XML, bindingsWith(binding)));

        assertEquals(3, node.getCopies());
        verify(printNodeService).updateNode(node);
    }

    @Test
    void saveFlowShouldDefaultMissingPrintCopiesToOne() {
        List<PrintNodeEntity> nodes = createFixedNodes();
        PrintNodeEntity node = findNode(nodes, "order.confirmed");
        PrintNodeBinding binding = new PrintNodeBinding(
                "order.confirmed",
                "printer-1",
                null,
                null,
                "PRINT",
                "MANUAL",
                null,
                true
        );
        when(printNodeService.listNodes()).thenReturn(nodes);
        when(printFlowMapper.selectById("default")).thenReturn(null);

        printFlowService.saveFlow(new PrintFlowSaveRequest(VALID_FLOW_XML, bindingsWith(binding)));

        assertEquals(1, node.getCopies());
    }

    @Test
    void saveFlowShouldRejectNonPositivePrintCopiesBeforeWriting() {
        PrintNodeBinding binding = new PrintNodeBinding(
                "order.confirmed",
                "printer-1",
                null,
                null,
                "PRINT",
                "MANUAL",
                0,
                true
        );
        when(printNodeService.listNodes()).thenReturn(createFixedNodes());

        assertThrows(
                IllegalArgumentException.class,
                () -> printFlowService.saveFlow(new PrintFlowSaveRequest(VALID_FLOW_XML, bindingsWith(binding)))
        );

        verify(printFlowMapper, never()).insert(any(PrintFlowEntity.class));
        verify(printNodeService, never()).updateNode(any(PrintNodeEntity.class));
    }

    @Test
    void saveFlowShouldRejectChangedFixedFlowTopologyBeforeWriting() {
        String changedFlowXml = VALID_FLOW_XML.replace(
                "sourceRef=\"Task_Kitchen\" targetRef=\"Task_Served\"",
                "sourceRef=\"Task_Kitchen\" targetRef=\"Task_Settle\""
        );
        when(printNodeService.listNodes()).thenReturn(createFixedNodes());

        assertThrows(
                IllegalArgumentException.class,
                () -> printFlowService.saveFlow(new PrintFlowSaveRequest(changedFlowXml, paperlessBindings()))
        );

        verify(printFlowMapper, never()).insert(any(PrintFlowEntity.class));
        verify(printNodeService, never()).updateNode(any(PrintNodeEntity.class));
    }

    private PrintNodeEntity createNode(String nodeName) {
        PrintNodeEntity node = new PrintNodeEntity();
        node.setId("node-1");
        node.setNodeName(nodeName);
        node.setDisplayName("订单确认");
        node.setEnabled(true);
        return node;
    }

    private List<PrintNodeEntity> createFixedNodes() {
        return FIXED_NODE_NAMES.stream().map(this::createNode).toList();
    }

    private PrintNodeEntity findNode(List<PrintNodeEntity> nodes, String nodeName) {
        return nodes.stream()
                .filter(node -> nodeName.equals(node.getNodeName()))
                .findFirst()
                .orElseThrow();
    }

    private List<PrintNodeBinding> paperlessBindings() {
        return FIXED_NODE_NAMES.stream().map(this::paperlessBinding).toList();
    }

    private List<PrintNodeBinding> bindingsWith(PrintNodeBinding replacement) {
        return FIXED_NODE_NAMES.stream().map(nodeName -> nodeName.equals(replacement.nodeName())
                ? replacement
                : paperlessBinding(nodeName)).toList();
    }

    private PrintNodeBinding paperlessBinding(String nodeName) {
        return new PrintNodeBinding(nodeName, null, null, null, "PAPERLESS", "MANUAL", 1, true);
    }

    private static String fixedFlowXml(String billingMode) {
        List<String> sequence = "PREPAID".equals(billingMode)
                ? List.of(
                        "StartEvent_1",
                        "Task_Submit",
                        "Task_Settle",
                        "Task_Frontdesk",
                        "Task_Kitchen",
                        "Task_Served",
                        "EndEvent_1"
                )
                : List.of(
                        "StartEvent_1",
                        "Task_Submit",
                        "Task_Frontdesk",
                        "Task_Kitchen",
                        "Task_Served",
                        "Task_Settle",
                        "EndEvent_1"
                );
        StringBuilder xml = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                    xmlns:jnimble="https://jnimble.com/schema/bpmn">
                  <bpmn:process id="Process_1">
                """);
        for (String elementId : sequence) {
            if ("StartEvent_1".equals(elementId)) {
                xml.append("<bpmn:startEvent id=\"").append(elementId).append("\" />");
            } else if ("Task_Submit".equals(elementId)) {
                xml.append("<bpmn:userTask id=\"").append(elementId).append("\" />");
            } else if ("EndEvent_1".equals(elementId)) {
                xml.append("<bpmn:endEvent id=\"").append(elementId).append("\" />");
            } else {
                xml.append("<bpmn:serviceTask id=\"").append(elementId)
                        .append("\" jnimble:nodeName=\"")
                        .append(nodeNameForTask(elementId))
                        .append("\" />");
            }
        }
        for (int index = 1; index < sequence.size(); index += 1) {
            xml.append("<bpmn:sequenceFlow id=\"Flow_").append(index)
                    .append("\" sourceRef=\"").append(sequence.get(index - 1))
                    .append("\" targetRef=\"").append(sequence.get(index))
                    .append("\" />");
        }
        return xml.append("</bpmn:process></bpmn:definitions>").toString();
    }

    private static String nodeNameForTask(String taskId) {
        return switch (taskId) {
            case "Task_Frontdesk" -> "order.confirmed";
            case "Task_Kitchen" -> "order.kitchen.confirmed";
            case "Task_Served" -> "order.served";
            case "Task_Settle" -> "order.settled";
            default -> throw new IllegalArgumentException("Unknown fixed task: " + taskId);
        };
    }
}
