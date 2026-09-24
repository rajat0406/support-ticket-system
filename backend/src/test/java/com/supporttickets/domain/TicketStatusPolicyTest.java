package com.supporttickets.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TicketStatusPolicyTest {

    @Test
    void containsExactlyFiveAllowedEdges() {
        int allowed = 0;
        for (TicketStatus current : TicketStatus.values()) {
            for (TicketStatus requested : TicketStatus.values()) {
                if (TicketStatusPolicy.isAllowed(current, requested)) {
                    allowed++;
                }
            }
        }
        assertThat(allowed).isEqualTo(5);
        assertThat(TicketStatusPolicy.isAllowed(TicketStatus.OPEN, TicketStatus.IN_PROGRESS)).isTrue();
        assertThat(TicketStatusPolicy.isAllowed(TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED)).isTrue();
        assertThat(TicketStatusPolicy.isAllowed(TicketStatus.RESOLVED, TicketStatus.CLOSED)).isTrue();
        assertThat(TicketStatusPolicy.isAllowed(TicketStatus.OPEN, TicketStatus.CANCELLED)).isTrue();
        assertThat(TicketStatusPolicy.isAllowed(TicketStatus.IN_PROGRESS, TicketStatus.CANCELLED)).isTrue();
    }

    @ParameterizedTest(name = "{0} -> {1} valid={2}")
    @CsvSource({
            "OPEN, OPEN, false",
            "OPEN, IN_PROGRESS, true",
            "OPEN, RESOLVED, false",
            "OPEN, CLOSED, false",
            "OPEN, CANCELLED, true",
            "IN_PROGRESS, OPEN, false",
            "IN_PROGRESS, IN_PROGRESS, false",
            "IN_PROGRESS, RESOLVED, true",
            "IN_PROGRESS, CLOSED, false",
            "IN_PROGRESS, CANCELLED, true",
            "RESOLVED, OPEN, false",
            "RESOLVED, IN_PROGRESS, false",
            "RESOLVED, RESOLVED, false",
            "RESOLVED, CLOSED, true",
            "RESOLVED, CANCELLED, false",
            "CLOSED, OPEN, false",
            "CLOSED, IN_PROGRESS, false",
            "CLOSED, RESOLVED, false",
            "CLOSED, CLOSED, false",
            "CLOSED, CANCELLED, false",
            "CANCELLED, OPEN, false",
            "CANCELLED, IN_PROGRESS, false",
            "CANCELLED, RESOLVED, false",
            "CANCELLED, CLOSED, false",
            "CANCELLED, CANCELLED, false"
    })
    void evaluatesAllTwentyFivePairs(TicketStatus current, TicketStatus requested, boolean expected) {
        assertThat(TicketStatusPolicy.isAllowed(current, requested)).isEqualTo(expected);
    }
}
