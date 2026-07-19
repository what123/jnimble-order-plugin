package com.jnimble.plugin.printer.service;

import com.jnimble.plugin.printer.model.dto.PrintTemplateViolation;
import java.util.List;

public class PrintTemplateValidationException extends IllegalArgumentException {

    private final List<PrintTemplateViolation> violations;

    public PrintTemplateValidationException(List<PrintTemplateViolation> violations) {
        super("Print template validation failed");
        this.violations = List.copyOf(violations);
    }

    public List<PrintTemplateViolation> getViolations() {
        return violations;
    }
}
