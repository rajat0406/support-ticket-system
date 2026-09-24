package com.supporttickets.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.supporttickets.domain.TicketStatus;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        String code,
        String message,
        TicketStatus currentStatus,
        TicketStatus requestedStatus
) {
    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(code, message, null, null);
    }

    public static ApiErrorResponse unexpected() {
        return new ApiErrorResponse(null, "An unexpected error occurred.", null, null);
    }
}
