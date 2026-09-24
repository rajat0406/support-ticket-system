package com.supporttickets.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TicketDetailDto(
        UUID id,
        String title,
        String description,
        String priority,
        String assignee,
        String status,
        Instant createdAt,
        Instant updatedAt,
        List<CommentDto> comments
) {
}
