package com.supporttickets.domain;

import java.util.UUID;

public class TicketNotFoundException extends RuntimeException {

    private final UUID ticketId;

    public TicketNotFoundException(UUID ticketId) {
        super("Ticket was not found.");
        this.ticketId = ticketId;
    }

    public String code() {
        return DomainErrorCodes.TICKET_NOT_FOUND;
    }

    public UUID ticketId() {
        return ticketId;
    }
}
