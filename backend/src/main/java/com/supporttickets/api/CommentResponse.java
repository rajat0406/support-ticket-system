package com.supporttickets.api;

import java.time.Instant;
import java.util.UUID;

import com.supporttickets.application.CommentDto;

public record CommentResponse(
        UUID id,
        String body,
        String author,
        Instant createdAt
) {
    public static CommentResponse from(CommentDto dto) {
        return new CommentResponse(dto.id(), dto.body(), dto.author(), dto.createdAt());
    }
}
