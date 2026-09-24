package com.supporttickets.domain;

public final class DomainErrorCodes {

    public static final String ILLEGAL_STATUS_TRANSITION = "ILLEGAL_STATUS_TRANSITION";
    public static final String TICKET_NOT_FOUND = "TICKET_NOT_FOUND";
    public static final String INVALID_STATUS_VALUE = "INVALID_STATUS_VALUE";
    public static final String INVALID_REQUEST = "INVALID_REQUEST";
    public static final String STATUS_NOT_UPDATABLE = "STATUS_NOT_UPDATABLE";

    private DomainErrorCodes() {
    }
}
