package com.supporttickets.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class TicketStatusTest {

    @Test
    void containsExactlyFiveApprovedLabels() {
        assertThat(TicketStatus.values()).containsExactly(
                TicketStatus.OPEN,
                TicketStatus.IN_PROGRESS,
                TicketStatus.RESOLVED,
                TicketStatus.CLOSED,
                TicketStatus.CANCELLED
        );
    }

    @ParameterizedTest
    @EnumSource(TicketStatus.class)
    void fromLabelAcceptsExactEnumName(TicketStatus status) {
        assertThat(TicketStatus.fromLabel(status.name())).isEqualTo(status);
    }

    @ParameterizedTest
    @CsvSource({"open", "Closed", "DONE"})
    void fromLabelRejectsUnknownOrWrongCase(String label) {
        assertThatThrownBy(() -> TicketStatus.fromLabel(label))
                .isInstanceOf(InvalidStatusValueException.class)
                .extracting(ex -> ((InvalidStatusValueException) ex).code())
                .isEqualTo(DomainErrorCodes.INVALID_STATUS_VALUE);
    }

    @Test
    void fromLabelRejectsNull() {
        assertThatThrownBy(() -> TicketStatus.fromLabel(null))
                .isInstanceOf(InvalidStatusValueException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    void fromLabelRejectsBlank(String label) {
        assertThatThrownBy(() -> TicketStatus.fromLabel(label))
                .isInstanceOf(InvalidStatusValueException.class);
    }
}
