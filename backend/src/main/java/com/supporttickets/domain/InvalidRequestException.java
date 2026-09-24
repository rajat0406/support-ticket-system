package com.supporttickets.domain;

public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }

    public String code() {
        return DomainErrorCodes.INVALID_REQUEST;
    }
}
