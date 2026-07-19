package com.jnimble.plugin.printer.service;

import com.jnimble.platform.persistence.crud.MapperUtils;
import com.jnimble.plugin.printer.mapper.PrintFlowMapper;
import com.jnimble.plugin.printer.model.dto.PrintFlowSaveRequest;
import com.jnimble.plugin.printer.model.dto.PrintNodeBinding;
import com.jnimble.plugin.printer.model.entity.PrintFlowEntity;
import com.jnimble.plugin.printer.model.entity.PrintNodeEntity;
import java.io.IOException;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

@Service
public class PrintFlowService {

    private static final String DEFAULT_FLOW_ID = "default";
    private static final String DEFAULT_BILLING_MODE = "POSTPAID";
    private static final String DEFAULT_CONFIRMATION_MODE = "MANUAL";
    private static final String OPERATION_MODE_PRINT = "PRINT";
    private static final String OPERATION_MODE_PAPERLESS = "PAPERLESS";
    private static final String BPMN_NAMESPACE = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    private static final String JNIMBLE_NAMESPACE = "https://jnimble.com/schema/bpmn";
    private static final int MAX_FLOW_XML_LENGTH = 1_000_000;
    private static final int MAX_TEMPLATE_VIEW_LENGTH = 200;
    private static final int MAX_TEMPLATE_ID_LENGTH = 64;
    private static final int DEFAULT_PRINT_COPIES = 1;
    private static final Set<String> BILLING_MODES = Set.of("POSTPAID", "PREPAID");
    private static final Set<String> OPERATION_MODES = Set.of(OPERATION_MODE_PRINT, OPERATION_MODE_PAPERLESS);
    private static final Set<String> CONFIRMATION_MODES = Set.of("AUTO", DEFAULT_CONFIRMATION_MODE);
    private static final Set<String> FIXED_PROCESS_ELEMENT_TYPES = Set.of(
            "startEvent",
            "endEvent",
            "userTask",
            "serviceTask",
            "sequenceFlow"
    );
    private static final List<String> POSTPAID_FLOW_SEQUENCE = List.of(
            "StartEvent_1",
            "Task_Submit",
            "Task_Frontdesk",
            "Task_Kitchen",
            "Task_Served",
            "Task_Settle",
            "EndEvent_1"
    );
    private static final List<String> PREPAID_FLOW_SEQUENCE = List.of(
            "StartEvent_1",
            "Task_Submit",
            "Task_Settle",
            "Task_Frontdesk",
            "Task_Kitchen",
            "Task_Served",
            "EndEvent_1"
    );
    private static final Map<String, String> FIXED_ELEMENT_TYPES = Map.of(
            "StartEvent_1", "startEvent",
            "Task_Submit", "userTask",
            "Task_Frontdesk", "serviceTask",
            "Task_Kitchen", "serviceTask",
            "Task_Served", "serviceTask",
            "Task_Settle", "serviceTask",
            "EndEvent_1", "endEvent"
    );
    private static final Map<String, String> FIXED_SERVICE_TASK_NODE_NAMES = Map.of(
            "Task_Frontdesk", "order.confirmed",
            "Task_Kitchen", "order.kitchen.confirmed",
            "Task_Served", "order.served",
            "Task_Settle", "order.settled"
    );

    private final PrintFlowMapper printFlowMapper;
    private final PrintNodeService printNodeService;
    private final PrintTemplateService printTemplateService;

    public PrintFlowService(
            PrintFlowMapper printFlowMapper,
            PrintNodeService printNodeService,
            PrintTemplateService printTemplateService
    ) {
        this.printFlowMapper = printFlowMapper;
        this.printNodeService = printNodeService;
        this.printTemplateService = printTemplateService;
    }

    @Transactional(readOnly = true)
    public String getFlowXml() {
        PrintFlowEntity entity = MapperUtils.getById(printFlowMapper, DEFAULT_FLOW_ID, null);
        return entity == null ? null : entity.getFlowXml();
    }

    @Transactional(readOnly = true)
    public String getBillingMode() {
        PrintFlowEntity entity = MapperUtils.getById(printFlowMapper, DEFAULT_FLOW_ID, null);
        return entity == null || entity.getBillingMode() == null
                ? DEFAULT_BILLING_MODE
                : entity.getBillingMode();
    }

    @Transactional
    public void saveFlow(PrintFlowSaveRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Flow request is required");
        }

        String billingMode = normalizeChoice(
                request.billingMode(),
                BILLING_MODES,
                DEFAULT_BILLING_MODE,
                "billing mode"
        );
        String flowXml = validateFlowXml(request.xml(), billingMode);
        Map<String, PrintNodeEntity> nodesByName = registeredNodesByName();
        Map<String, PrintNodeBinding> bindingsByName = validateBindings(request, nodesByName, billingMode);

        saveFlowXml(flowXml, billingMode);
        bindingsByName.forEach((nodeName, binding) -> updateNodeBinding(nodesByName.get(nodeName), binding));
    }

    private Map<String, PrintNodeEntity> registeredNodesByName() {
        Map<String, PrintNodeEntity> nodesByName = new HashMap<>();
        for (PrintNodeEntity node : printNodeService.listNodes()) {
            nodesByName.put(node.getNodeName(), node);
        }
        return nodesByName;
    }

    private Map<String, PrintNodeBinding> validateBindings(
            PrintFlowSaveRequest request,
            Map<String, PrintNodeEntity> nodesByName,
            String billingMode
    ) {
        Map<String, PrintNodeBinding> bindingsByName = new HashMap<>();
        Set<String> seenNodeNames = new HashSet<>();
        Set<String> fixedNodeNames = Set.copyOf(FIXED_SERVICE_TASK_NODE_NAMES.values());

        for (PrintNodeBinding binding : request.nodes()) {
            if (binding == null || binding.nodeName() == null || binding.nodeName().isBlank()) {
                throw new IllegalArgumentException("Every print node binding must have a node name");
            }

            String nodeName = binding.nodeName().trim();
            if (!nodesByName.containsKey(nodeName)) {
                throw new IllegalArgumentException("Unknown print node: " + nodeName);
            }
            if (!seenNodeNames.add(nodeName)) {
                throw new IllegalArgumentException("Duplicate print node binding: " + nodeName);
            }
            if (!fixedNodeNames.contains(nodeName)) {
                throw new IllegalArgumentException("Print node is not part of the fixed flow: " + nodeName);
            }

            String printerId = trimToNull(binding.printerId());
            String operationMode = normalizeChoice(
                    binding.operationMode(),
                    OPERATION_MODES,
                    printerId == null ? OPERATION_MODE_PAPERLESS : OPERATION_MODE_PRINT,
                    "operation mode"
            );
            String confirmationMode = normalizeChoice(
                    binding.confirmationMode(),
                    CONFIRMATION_MODES,
                    DEFAULT_CONFIRMATION_MODE,
                    "confirmation mode"
            );
            if (OPERATION_MODE_PRINT.equals(operationMode) && printerId == null) {
                throw new IllegalArgumentException("A printer is required for print operation mode");
            }

            String templateView = OPERATION_MODE_PRINT.equals(operationMode)
                    ? trimToNull(binding.templateView())
                    : null;
            String templateId = OPERATION_MODE_PRINT.equals(operationMode)
                    ? trimToNull(binding.templateId())
                    : null;
            if (templateView != null && templateView.length() > MAX_TEMPLATE_VIEW_LENGTH) {
                throw new IllegalArgumentException("Print template path is too long");
            }
            if (templateId != null && templateId.length() > MAX_TEMPLATE_ID_LENGTH) {
                throw new IllegalArgumentException("Print template ID is too long");
            }
            if (templateId != null && !printTemplateService.isPublishedTemplate(templateId)) {
                throw new IllegalArgumentException("Print template must exist and be published");
            }
            int copies = OPERATION_MODE_PRINT.equals(operationMode)
                    ? normalizeCopies(binding.copies())
                    : DEFAULT_PRINT_COPIES;

            bindingsByName.put(nodeName, new PrintNodeBinding(
                    nodeName,
                    OPERATION_MODE_PRINT.equals(operationMode) ? printerId : null,
                    templateView,
                    templateId,
                    operationMode,
                    confirmationMode,
                    copies,
                    binding.enabled()
            ));
        }
        if (!seenNodeNames.equals(fixedNodeNames)) {
            Set<String> missingNodeNames = new HashSet<>(fixedNodeNames);
            missingNodeNames.removeAll(seenNodeNames);
            throw new IllegalArgumentException("Missing fixed print node bindings: " + missingNodeNames);
        }
        return bindingsByName;
    }

    private void saveFlowXml(String flowXml, String billingMode) {
        LocalDateTime now = LocalDateTime.now();
        PrintFlowEntity entity = MapperUtils.getById(printFlowMapper, DEFAULT_FLOW_ID, null);
        if (entity == null) {
            entity = new PrintFlowEntity();
            entity.setId(DEFAULT_FLOW_ID);
            entity.setCreatedAt(now);
            entity.setBillingMode(billingMode);
            entity.setFlowXml(flowXml);
            entity.setUpdatedAt(now);
            MapperUtils.insert(printFlowMapper, entity);
            return;
        }

        entity.setBillingMode(billingMode);
        entity.setFlowXml(flowXml);
        entity.setUpdatedAt(now);
        MapperUtils.updateById(printFlowMapper, entity);
    }

    private void updateNodeBinding(PrintNodeEntity node, PrintNodeBinding binding) {
        node.setPrinterId(binding.printerId());
        node.setTemplateView(binding.templateView());
        node.setTemplateId(binding.templateId());
        node.setOperationMode(binding.operationMode());
        node.setConfirmationMode(binding.confirmationMode());
        node.setCopies(binding.copies());
        node.setEnabled(binding.enabled() == null ? Boolean.TRUE : binding.enabled());
        printNodeService.updateNode(node);
    }

    private String validateFlowXml(String flowXml, String billingMode) {
        if (flowXml == null || flowXml.isBlank()) {
            throw new IllegalArgumentException("Flow XML is required");
        }
        if (flowXml.length() > MAX_FLOW_XML_LENGTH) {
            throw new IllegalArgumentException("Flow XML is too large");
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(flowXml)));
            Element root = document.getDocumentElement();
            boolean isBpmnDefinitions = root != null
                    && "definitions".equals(root.getLocalName())
                    && BPMN_NAMESPACE.equals(root.getNamespaceURI());
            if (!isBpmnDefinitions
                    || document.getElementsByTagNameNS(BPMN_NAMESPACE, "process").getLength() == 0) {
                throw new IllegalArgumentException("Flow XML must contain BPMN definitions and a process");
            }
            validateFixedFlow(document, billingMode);
            return flowXml;
        } catch (ParserConfigurationException | SAXException | IOException exception) {
            throw new IllegalArgumentException("Invalid BPMN XML", exception);
        }
    }

    private void validateFixedFlow(Document document, String billingMode) {
        var processes = document.getElementsByTagNameNS(BPMN_NAMESPACE, "process");
        if (processes.getLength() != 1) {
            throw new IllegalArgumentException("The fixed print flow must contain exactly one process");
        }

        List<String> expectedSequence = expectedFlowSequence(billingMode);
        Set<String> expectedElementIds = Set.copyOf(expectedSequence);
        Set<String> expectedFlowIds = new HashSet<>();
        for (int index = 1; index < expectedSequence.size(); index += 1) {
            expectedFlowIds.add("Flow_" + index);
        }

        Map<String, Element> elementsById = new HashMap<>();
        Map<String, Element> flowsById = new HashMap<>();
        Element process = (Element) processes.item(0);
        var childNodes = process.getChildNodes();
        for (int index = 0; index < childNodes.getLength(); index += 1) {
            org.w3c.dom.Node child = childNodes.item(index);
            if (child.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) child;
            if (!BPMN_NAMESPACE.equals(element.getNamespaceURI())
                    || !FIXED_PROCESS_ELEMENT_TYPES.contains(element.getLocalName())) {
                throw new IllegalArgumentException("The print flow contains unsupported elements");
            }
            String id = trimToNull(element.getAttribute("id"));
            if (id == null) {
                throw new IllegalArgumentException("Every fixed flow element must have an ID");
            }
            Map<String, Element> target = "sequenceFlow".equals(element.getLocalName())
                    ? flowsById
                    : elementsById;
            if (target.put(id, element) != null) {
                throw new IllegalArgumentException("The fixed flow contains duplicate element IDs");
            }
        }

        if (!elementsById.keySet().equals(expectedElementIds) || !flowsById.keySet().equals(expectedFlowIds)) {
            throw new IllegalArgumentException("The print flow nodes or lines cannot be changed");
        }
        for (String elementId : expectedSequence) {
            Element element = elementsById.get(elementId);
            if (!FIXED_ELEMENT_TYPES.get(elementId).equals(element.getLocalName())) {
                throw new IllegalArgumentException("The print flow nodes or lines cannot be changed");
            }
        }
        for (int index = 1; index < expectedSequence.size(); index += 1) {
            Element flow = flowsById.get("Flow_" + index);
            if (!expectedSequence.get(index - 1).equals(flow.getAttribute("sourceRef"))
                    || !expectedSequence.get(index).equals(flow.getAttribute("targetRef"))) {
                throw new IllegalArgumentException("The print flow nodes or lines cannot be changed");
            }
        }
        for (Map.Entry<String, String> entry : FIXED_SERVICE_TASK_NODE_NAMES.entrySet()) {
            Element serviceTask = elementsById.get(entry.getKey());
            String nodeName = trimToNull(serviceTask.getAttributeNS(JNIMBLE_NAMESPACE, "nodeName"));
            if (nodeName == null) {
                nodeName = trimToNull(serviceTask.getAttribute("jnimble:nodeName"));
            }
            if (!entry.getValue().equals(nodeName)) {
                throw new IllegalArgumentException("The print flow node binding cannot be changed");
            }
        }
    }

    private List<String> expectedFlowSequence(String billingMode) {
        return "PREPAID".equals(billingMode) ? PREPAID_FLOW_SEQUENCE : POSTPAID_FLOW_SEQUENCE;
    }

    private int normalizeCopies(Integer copies) {
        if (copies == null) {
            return DEFAULT_PRINT_COPIES;
        }
        if (copies < DEFAULT_PRINT_COPIES) {
            throw new IllegalArgumentException("Print copies must be greater than zero");
        }
        return copies;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeChoice(String value, Set<String> supported, String fallback, String fieldName) {
        String normalized = value == null || value.isBlank()
                ? fallback
                : value.trim().toUpperCase(Locale.ROOT);
        if (!supported.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported " + fieldName + ": " + value);
        }
        return normalized;
    }
}
