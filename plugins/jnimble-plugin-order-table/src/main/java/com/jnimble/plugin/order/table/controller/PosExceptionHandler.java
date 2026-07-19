package com.jnimble.plugin.order.table.controller;

import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = PosController.class)
public class PosExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> badRequest(IllegalArgumentException exception) {
        return Map.of("success", false, "message", message(exception));
    }

    @ExceptionHandler({IllegalStateException.class, DataIntegrityViolationException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> conflict(RuntimeException exception) {
        return Map.of("success", false, "message", message(exception));
    }

    private String message(RuntimeException exception) {
        return exception.getMessage() == null ? "Operation failed" : exception.getMessage();
    }
}
