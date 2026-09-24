package com.supporttickets.api;

import jakarta.validation.constraints.NotBlank;

public record AddCommentRequest(
        @NotBlank String body,
        String author
) {
}
