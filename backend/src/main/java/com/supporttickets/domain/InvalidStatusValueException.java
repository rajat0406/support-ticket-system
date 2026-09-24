package com.supporttickets.domain;

public class InvalidStatusValueException extends RuntimeException {

    private final String requestedValue;

    public InvalidStatusValueException(String requestedValue) {
        super("Status value is not one of the five allowed labels.");
        this.requestedValue = requestedValue;
    }

    public String code() {
        return DomainErrorCodes.INVALID_STATUS_VALUE;
    }

    public String requestedValue() {
        return requestedValue;
    }
}
