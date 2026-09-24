package com.supporttickets.api;

import java.time.Instant;
import java.util.UUID;

import com.supporttickets.application.TicketSummaryDto;

public record TicketSummaryResponse(
        UUID id,
        String title,
        String description,
        String priority,
        String assignee,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
    public static TicketSummaryResponse from(TicketSummaryDto ticket) {
        return new TicketSummaryResponse(
                ticket.id(),
                ticket.title(),
                ticket.description(),
                ticket.priority(),
                ticket.assignee(),
                ticket.status(),
                ticket.createdAt(),
                ticket.updatedAt()
        );
    }
}
