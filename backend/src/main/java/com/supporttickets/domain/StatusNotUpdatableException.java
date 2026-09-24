package com.supporttickets.domain;

public class StatusNotUpdatableException extends RuntimeException {

    public StatusNotUpdatableException() {
        super("Status cannot be set on create or field update.");
    }

    public String code() {
        return DomainErrorCodes.STATUS_NOT_UPDATABLE;
    }
}
