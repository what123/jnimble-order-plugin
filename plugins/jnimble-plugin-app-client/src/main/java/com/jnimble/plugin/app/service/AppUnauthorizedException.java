package com.jnimble.plugin.app.service;

public class AppUnauthorizedException extends RuntimeException {

    public AppUnauthorizedException(String message) {
        super(message);
    }
}
