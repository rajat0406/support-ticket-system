package com.supporttickets.api;

import jakarta.validation.constraints.NotBlank;

public record TransitionTicketRequest(@NotBlank String requestedStatus) {
}
