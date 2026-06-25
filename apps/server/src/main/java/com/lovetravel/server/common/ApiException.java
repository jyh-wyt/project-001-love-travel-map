package com.lovetravel.server.common;

public class ApiException extends RuntimeException {

    public ApiException(String message) {
        super(message);
    }
}

