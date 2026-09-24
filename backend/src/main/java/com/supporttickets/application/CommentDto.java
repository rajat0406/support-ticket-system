package com.supporttickets.application;

import java.time.Instant;
import java.util.UUID;

public record CommentDto(
        UUID id,
        String body,
        String author,
        Instant createdAt
) {
}
