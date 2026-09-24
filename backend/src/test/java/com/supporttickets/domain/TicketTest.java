package com.supporttickets.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TicketTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-24T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void createStartsOpen() {
        Ticket ticket = Ticket.create("Printer down", "No toner", null, null, CLOCK);
        assertThat(ticket.status()).isEqualTo(TicketStatus.OPEN);
        assertThat(ticket.title()).isEqualTo("Printer down");
    }

    @Test
    void createRejectsBlankTitle() {
        assertThatThrownBy(() -> Ticket.create("  ", "desc", null, null, CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
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
    void transitionToMatchesApprovedTable(TicketStatus current, TicketStatus requested, boolean valid) {
        Ticket ticket = ticketIn(current);
        Instant updatedBefore = ticket.updatedAt();
        if (valid) {
            ticket.transitionTo(requested, CLOCK);
            assertThat(ticket.status()).isEqualTo(requested);
        } else {
            assertThatThrownBy(() -> ticket.transitionTo(requested, CLOCK))
                    .isInstanceOf(IllegalStatusTransitionException.class)
                    .satisfies(ex -> {
                        IllegalStatusTransitionException illegal = (IllegalStatusTransitionException) ex;
                        assertThat(illegal.code()).isEqualTo(DomainErrorCodes.ILLEGAL_STATUS_TRANSITION);
                        assertThat(illegal.currentStatus()).isEqualTo(current);
                        assertThat(illegal.requestedStatus()).isEqualTo(requested);
                    });
            assertThat(ticket.status()).isEqualTo(current);
            assertThat(ticket.updatedAt()).isEqualTo(updatedBefore);
        }
    }

    @Test
    void nullRequestedStatusIsInvalidValueNotIllegalTransition() {
        Ticket ticket = ticketIn(TicketStatus.OPEN);
        assertThatThrownBy(() -> ticket.transitionTo(null, CLOCK))
                .isInstanceOf(InvalidStatusValueException.class)
                .extracting(ex -> ((InvalidStatusValueException) ex).code())
                .isEqualTo(DomainErrorCodes.INVALID_STATUS_VALUE);
        assertThat(ticket.status()).isEqualTo(TicketStatus.OPEN);
    }

    @Test
    void updateFieldsDoesNotChangeStatus() {
        Ticket ticket = ticketIn(TicketStatus.IN_PROGRESS);
        ticket.updateFields("New title", "New desc", "HIGH", "alex", CLOCK);
        assertThat(ticket.status()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(ticket.title()).isEqualTo("New title");
        assertThat(ticket.assignee()).isEqualTo("alex");
    }

    @Test
    void addCommentDoesNotChangeStatus() {
        Ticket ticket = ticketIn(TicketStatus.OPEN);
        ticket.addComment("Looking into it", null, CLOCK);
        assertThat(ticket.status()).isEqualTo(TicketStatus.OPEN);
        assertThat(ticket.comments()).hasSize(1);
        assertThat(ticket.comments().getFirst().body()).isEqualTo("Looking into it");
    }

    @Test
    void addCommentRejectsBlankBody() {
        Ticket ticket = ticketIn(TicketStatus.OPEN);
        assertThatThrownBy(() -> ticket.addComment("  ", "alex", CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(ticket.comments()).isEmpty();
    }

    @Test
    void ticketNotFoundExceptionCarriesCode() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        TicketNotFoundException ex = new TicketNotFoundException(id);
        assertThat(ex.code()).isEqualTo(DomainErrorCodes.TICKET_NOT_FOUND);
        assertThat(ex.ticketId()).isEqualTo(id);
    }

    private static Ticket ticketIn(TicketStatus status) {
        Instant now = Instant.parse("2026-09-24T12:00:00Z");
        return Ticket.reconstitute(
                UUID.fromString("00000000-0000-0000-0000-00000000000a"),
                "Fixture",
                "Body",
                null,
                null,
                status,
                now,
                now
        );
    }
}
