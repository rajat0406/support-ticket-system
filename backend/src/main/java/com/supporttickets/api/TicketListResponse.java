package com.supporttickets.api;

import java.util.List;

import com.supporttickets.application.TicketSummaryDto;

public record TicketListResponse(List<TicketSummaryResponse> tickets) {

    public static TicketListResponse from(List<TicketSummaryDto> tickets) {
        return new TicketListResponse(tickets.stream().map(TicketSummaryResponse::from).toList());
    }
}
