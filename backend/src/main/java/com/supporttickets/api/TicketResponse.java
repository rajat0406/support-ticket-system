package com.supporttickets.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.supporttickets.application.TicketDetailDto;

public record TicketResponse(
        UUID id,
        String title,
        String description,
        String priority,
        String assignee,
        String status,
        Instant createdAt,
        Instant updatedAt,
        List<CommentResponse> comments
) {
    public static TicketResponse from(TicketDetailDto ticket) {
        return new TicketResponse(
                ticket.id(),
                ticket.title(),
                ticket.description(),
                ticket.priority(),
                ticket.assignee(),
                ticket.status(),
                ticket.createdAt(),
                ticket.updatedAt(),
                ticket.comments().stream().map(CommentResponse::from).toList()
        );
    }
}
