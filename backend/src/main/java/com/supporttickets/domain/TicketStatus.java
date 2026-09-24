package com.supporttickets.domain;

public enum TicketStatus {
    OPEN,
    IN_PROGRESS,
    RESOLVED,
    CLOSED,
    CANCELLED;

    public static TicketStatus fromLabel(String label) {
        if (label == null || label.isBlank()) {
            throw new InvalidStatusValueException(label);
        }
        try {
            return TicketStatus.valueOf(label);
        } catch (IllegalArgumentException ex) {
            throw new InvalidStatusValueException(label);
        }
    }
}
