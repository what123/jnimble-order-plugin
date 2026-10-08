package com.jnimble.plugin.app.service;

public class AppForbiddenException extends RuntimeException {

    public AppForbiddenException(String message) {
        super(message);
    }
}
