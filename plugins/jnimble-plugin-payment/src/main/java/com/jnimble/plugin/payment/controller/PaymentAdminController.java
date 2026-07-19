package com.jnimble.plugin.payment.controller;

import com.jnimble.plugin.payment.model.entity.PaymentEventEntity;
import com.jnimble.plugin.payment.service.PaymentDiagnosticService;
import com.jnimble.plugin.payment.service.PaymentService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/admin/plugins/payment")
public class PaymentAdminController {

    private final PaymentService paymentService;
    private final PaymentDiagnosticService diagnosticService;

    public PaymentAdminController(
            PaymentService paymentService,
            PaymentDiagnosticService diagnosticService
    ) {
        this.paymentService = paymentService;
        this.diagnosticService = diagnosticService;
    }

    @GetMapping("/records")
    public String recordsPage(Map<String, Object> model) {
        model.put("records", paymentService.listRecent(100));
        return "plugin/payment/admin/records";
    }

    @GetMapping("/diagnostics")
    public String diagnosticsPage(Map<String, Object> model) {
        model.put("events", diagnosticService.listRecent(100));
        return "plugin/payment/admin/diagnostics";
    }

    @GetMapping("/diagnostics/{id}")
    public String diagnosticDetailPage(@PathVariable String id, Map<String, Object> model) {
        PaymentEventEntity event = diagnosticService.findById(id);
        if (event == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment diagnostic event not found");
        }
        model.put("event", event);
        return "plugin/payment/admin/diagnostic-detail";
    }
}
