package com.fintech.reconciliation.domain.exception;

public class ProcessorUnavailableException extends RuntimeException {

    public ProcessorUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public ProcessorUnavailableException(String message) {
        super(message);
    }
}
