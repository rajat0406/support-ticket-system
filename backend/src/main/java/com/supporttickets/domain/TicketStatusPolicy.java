package com.supporttickets.domain;

import java.util.Objects;
import java.util.Set;

public final class TicketStatusPolicy {

    private record Edge(TicketStatus from, TicketStatus to) {
    }

    private static final Set<Edge> ALLOWED = Set.of(
            new Edge(TicketStatus.OPEN, TicketStatus.IN_PROGRESS),
            new Edge(TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED),
            new Edge(TicketStatus.RESOLVED, TicketStatus.CLOSED),
            new Edge(TicketStatus.OPEN, TicketStatus.CANCELLED),
            new Edge(TicketStatus.IN_PROGRESS, TicketStatus.CANCELLED)
    );

    private TicketStatusPolicy() {
    }

    public static boolean isAllowed(TicketStatus current, TicketStatus requested) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(requested, "requested");
        return ALLOWED.contains(new Edge(current, requested));
    }
}
