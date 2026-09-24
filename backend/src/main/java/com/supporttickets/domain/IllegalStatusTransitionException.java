package com.supporttickets.domain;

public class IllegalStatusTransitionException extends RuntimeException {

    private final TicketStatus currentStatus;
    private final TicketStatus requestedStatus;

    public IllegalStatusTransitionException(TicketStatus currentStatus, TicketStatus requestedStatus) {
        super("Transition from " + currentStatus + " to " + requestedStatus + " is not allowed.");
        this.currentStatus = currentStatus;
        this.requestedStatus = requestedStatus;
    }

    public String code() {
        return DomainErrorCodes.ILLEGAL_STATUS_TRANSITION;
    }

    public TicketStatus currentStatus() {
        return currentStatus;
    }

    public TicketStatus requestedStatus() {
        return requestedStatus;
    }
}
