package com.supporttickets.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.supporttickets.domain.DomainErrorCodes;
import com.supporttickets.domain.IllegalStatusTransitionException;
import com.supporttickets.domain.InvalidRequestException;
import com.supporttickets.domain.InvalidStatusValueException;
import com.supporttickets.domain.StatusNotUpdatableException;
import com.supporttickets.domain.TicketNotFoundException;
import com.supporttickets.domain.TicketStatus;
import com.supporttickets.persistence.InMemoryTicketRepository;

class TicketServiceTransitionTest {

    private InMemoryTicketRepository repository;
    private TicketService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryTicketRepository();
        service = new TicketService(repository, Clock.fixed(Instant.parse("2026-09-25T00:00:00Z"), ZoneOffset.UTC));
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
    void transitionMatchesApprovedTableAndDoesNotBypassPolicy(TicketStatus from, TicketStatus to, boolean valid) {
        TicketDetailDto ticket = ticketIn(from);
        Instant updatedBefore = ticket.updatedAt();
        if (valid) {
            TicketDetailDto updated = service.transition(ticket.id(), to.name());
            assertThat(updated.status()).isEqualTo(to.name());
            assertThat(service.get(ticket.id()).status()).isEqualTo(to.name());
        } else {
            assertThatThrownBy(() -> service.transition(ticket.id(), to.name()))
                    .isInstanceOf(IllegalStatusTransitionException.class)
                    .satisfies(ex -> {
                        IllegalStatusTransitionException illegal = (IllegalStatusTransitionException) ex;
                        assertThat(illegal.code()).isEqualTo(DomainErrorCodes.ILLEGAL_STATUS_TRANSITION);
                        assertThat(illegal.currentStatus()).isEqualTo(from);
                        assertThat(illegal.requestedStatus()).isEqualTo(to);
                    });
            TicketDetailDto unchanged = service.get(ticket.id());
            assertThat(unchanged.status()).isEqualTo(from.name());
            assertThat(unchanged.updatedAt()).isEqualTo(updatedBefore);
            assertThat(unchanged.title()).isEqualTo("Fixture");
        }
    }

    @Test
    void sameStateFromOpenIsRejected() {
        TicketDetailDto ticket = ticketIn(TicketStatus.OPEN);
        assertThatThrownBy(() -> service.transition(ticket.id(), "OPEN"))
                .isInstanceOf(IllegalStatusTransitionException.class);
        assertThat(service.get(ticket.id()).status()).isEqualTo("OPEN");
    }

    @Test
    void unknownLabelIsInvalidStatusValueNotIllegalTransition() {
        TicketDetailDto ticket = ticketIn(TicketStatus.OPEN);
        assertThatThrownBy(() -> service.transition(ticket.id(), "open"))
                .isInstanceOf(InvalidStatusValueException.class)
                .extracting(ex -> ((InvalidStatusValueException) ex).code())
                .isEqualTo(DomainErrorCodes.INVALID_STATUS_VALUE);
        assertThat(service.get(ticket.id()).status()).isEqualTo("OPEN");
    }

    @Test
    void nullRequestedStatusIsInvalidRequest() {
        TicketDetailDto ticket = ticketIn(TicketStatus.OPEN);
        assertThatThrownBy(() -> service.transition(ticket.id(), null))
                .isInstanceOf(InvalidRequestException.class)
                .extracting(ex -> ((InvalidRequestException) ex).code())
                .isEqualTo(DomainErrorCodes.INVALID_REQUEST);
        assertThat(service.get(ticket.id()).status()).isEqualTo("OPEN");
    }

    @Test
    void blankRequestedStatusIsInvalidRequest() {
        TicketDetailDto ticket = ticketIn(TicketStatus.OPEN);
        assertThatThrownBy(() -> service.transition(ticket.id(), "  "))
                .isInstanceOf(InvalidRequestException.class)
                .extracting(ex -> ((InvalidRequestException) ex).code())
                .isEqualTo(DomainErrorCodes.INVALID_REQUEST);
        assertThat(service.get(ticket.id()).status()).isEqualTo("OPEN");
    }

    @Test
    void missingTicket() {
        UUID missing = UUID.fromString("00000000-0000-0000-0000-000000000099");
        assertThatThrownBy(() -> service.transition(missing, "IN_PROGRESS"))
                .isInstanceOf(TicketNotFoundException.class);
    }

    @Test
    void fieldUpdateRejectsStatusAndLeavesLifecycleUnchanged() {
        TicketDetailDto ticket = ticketIn(TicketStatus.OPEN);
        assertThatThrownBy(() -> service.updateFields(
                ticket.id(), "t", "d", true, null, true, null, true, true))
                .isInstanceOf(StatusNotUpdatableException.class);
        assertThat(service.get(ticket.id()).status()).isEqualTo("OPEN");
        assertThat(service.get(ticket.id()).title()).isEqualTo("Fixture");
    }

    @Test
    void createRejectsClientStatus() {
        assertThatThrownBy(() -> service.create("Title", null, null, null, true))
                .isInstanceOf(StatusNotUpdatableException.class);
        assertThat(service.list(null, null)).isEmpty();
    }

    private TicketDetailDto ticketIn(TicketStatus status) {
        TicketDetailDto ticket = service.create("Fixture", "Body", null, null, false);
        if (status == TicketStatus.OPEN) {
            return ticket;
        }
        if (status == TicketStatus.IN_PROGRESS) {
            return service.transition(ticket.id(), "IN_PROGRESS");
        }
        if (status == TicketStatus.RESOLVED) {
            service.transition(ticket.id(), "IN_PROGRESS");
            return service.transition(ticket.id(), "RESOLVED");
        }
        if (status == TicketStatus.CLOSED) {
            service.transition(ticket.id(), "IN_PROGRESS");
            service.transition(ticket.id(), "RESOLVED");
            return service.transition(ticket.id(), "CLOSED");
        }
        return service.transition(ticket.id(), "CANCELLED");
    }
}
