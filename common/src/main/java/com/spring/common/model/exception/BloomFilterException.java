package com.spring.common.model.exception;

public class BloomFilterException extends RuntimeException {
    public BloomFilterException(String message) {
        super(message);
    }

    public BloomFilterException(Throwable cause) {
        super(cause);
    }
}
