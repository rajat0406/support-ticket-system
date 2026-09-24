package com.supporttickets.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.supporttickets.domain.DomainErrorCodes;
import com.supporttickets.domain.InvalidRequestException;
import com.supporttickets.domain.InvalidStatusValueException;
import com.supporttickets.domain.TicketNotFoundException;
import com.supporttickets.persistence.InMemoryTicketRepository;

class TicketServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-25T01:00:00Z"), ZoneOffset.UTC);

    private TicketService service;

    @BeforeEach
    void setUp() {
        service = new TicketService(new InMemoryTicketRepository(), CLOCK);
    }

    @Test
    void createPersistsOpenTicketWithoutComments() {
        TicketDetailDto created = service.create("Login page crashes", "Crash on submit", null, null, false);
        assertThat(created.status()).isEqualTo("OPEN");
        assertThat(created.comments()).isEmpty();
        TicketDetailDto loaded = service.get(created.id());
        assertThat(loaded.title()).isEqualTo("Login page crashes");
        assertThat(loaded.status()).isEqualTo("OPEN");
    }

    @Test
    void createRejectsBlankTitle() {
        assertThatThrownBy(() -> service.create("  ", "body", null, null, false))
                .isInstanceOf(InvalidRequestException.class)
                .extracting(ex -> ((InvalidRequestException) ex).code())
                .isEqualTo(DomainErrorCodes.INVALID_REQUEST);
        assertThat(service.list(null, null)).isEmpty();
    }

    @Test
    void getMissingTicket() {
        UUID missing = UUID.fromString("00000000-0000-0000-0000-000000000099");
        assertThatThrownBy(() -> service.get(missing)).isInstanceOf(TicketNotFoundException.class);
    }

    @Test
    void listReturnsAllWhenNoFilters() {
        service.create("Login page crashes", "Crash on submit", null, null, false);
        service.create("Password reset broken", "Email never arrives", null, null, false);
        assertThat(service.list(null, null)).hasSize(2);
        assertThat(service.list("  ", null)).hasSize(2);
    }

    @Test
    void keywordSearchIsCaseInsensitivePartialMatch() {
        seedSearchSet();
        List<TicketSummaryDto> loginHits = service.list("Login", null);
        assertThat(loginHits).extracting(TicketSummaryDto::title)
                .containsExactlyInAnyOrder("Login page crashes", "Login logo missing");
        assertThat(service.list("billing", null)).isEmpty();
        assertThat(service.list("EMAIL", null)).extracting(TicketSummaryDto::title)
                .containsExactly("Password reset broken");
    }

    @Test
    void statusFilterReturnsOnlyMatchingStatus() {
        seedSearchSet();
        assertThat(service.list(null, "CLOSED")).extracting(TicketSummaryDto::title)
                .containsExactly("Old SSO issue");
        assertThat(service.list(null, "OPEN")).hasSize(1);
    }

    @Test
    void keywordAndStatusCombineWithAnd() {
        seedSearchSet();
        assertThat(service.list("Login", "OPEN")).extracting(TicketSummaryDto::title)
                .containsExactly("Login page crashes");
        assertThat(service.list("Login", "CLOSED")).isEmpty();
    }

    @Test
    void invalidStatusFilterIsInvalidStatusValue() {
        service.create("Login page crashes", "Crash", null, null, false);
        assertThatThrownBy(() -> service.list(null, "open"))
                .isInstanceOf(InvalidStatusValueException.class);
        assertThatThrownBy(() -> service.list("Login", ""))
                .isInstanceOf(InvalidStatusValueException.class);
    }

    @Test
    void updateChangesAllowedFieldsAndLeavesStatus() {
        TicketDetailDto created = service.create("Old title", "Old desc", "LOW", "pat", false);
        TicketDetailDto updated = service.updateFields(
                created.id(), "New title", "New desc", true, null, true, "alex", true, false);
        assertThat(updated.title()).isEqualTo("New title");
        assertThat(updated.description()).isEqualTo("New desc");
        assertThat(updated.priority()).isNull();
        assertThat(updated.assignee()).isEqualTo("alex");
        assertThat(updated.status()).isEqualTo("OPEN");
    }

    @Test
    void updateOmitsOptionalFieldsLeaveStoredValues() {
        TicketDetailDto created = service.create("Old title", "Keep desc", "HIGH", "pat", false);
        TicketDetailDto updated = service.updateFields(
                created.id(), "New title", null, false, null, false, null, false, false);
        assertThat(updated.title()).isEqualTo("New title");
        assertThat(updated.description()).isEqualTo("Keep desc");
        assertThat(updated.priority()).isEqualTo("HIGH");
        assertThat(updated.assignee()).isEqualTo("pat");
    }

    @Test
    void updateBlankTitleIsInvalidRequest() {
        TicketDetailDto created = service.create("Title", "Body", null, null, false);
        assertThatThrownBy(() -> service.updateFields(
                created.id(), "  ", null, false, null, false, null, false, false))
                .isInstanceOf(InvalidRequestException.class);
        assertThat(service.get(created.id()).title()).isEqualTo("Title");
    }

    @Test
    void addCommentDoesNotChangeStatus() {
        TicketDetailDto created = service.create("Login page crashes", "Crash", null, null, false);
        CommentDto comment = service.addComment(created.id(), "Looking into it", "alex");
        assertThat(comment.body()).isEqualTo("Looking into it");
        assertThat(comment.author()).isEqualTo("alex");
        TicketDetailDto loaded = service.get(created.id());
        assertThat(loaded.status()).isEqualTo("OPEN");
        assertThat(loaded.updatedAt()).isEqualTo(created.updatedAt());
        assertThat(loaded.comments()).extracting(CommentDto::body).containsExactly("Looking into it");
    }

    @Test
    void addCommentRejectsBlankBody() {
        TicketDetailDto created = service.create("Title", "Body", null, null, false);
        assertThatThrownBy(() -> service.addComment(created.id(), "  ", null))
                .isInstanceOf(InvalidRequestException.class);
        assertThat(service.get(created.id()).comments()).isEmpty();
    }

    @Test
    void addCommentOnMissingTicket() {
        UUID missing = UUID.fromString("00000000-0000-0000-0000-000000000099");
        assertThatThrownBy(() -> service.addComment(missing, "Hello", null))
                .isInstanceOf(TicketNotFoundException.class);
    }

    private void seedSearchSet() {
        TicketDetailDto open = service.create("Login page crashes", "Crash on submit", null, null, false);
        TicketDetailDto inProgress = service.create("Password reset broken", "Email never arrives", null, null, false);
        TicketDetailDto resolved = service.create("Login logo missing", "Asset 404", null, null, false);
        TicketDetailDto closed = service.create("Old SSO issue", "Superseded", null, null, false);
        TicketDetailDto cancelled = service.create("Duplicate signup", "Created twice", null, null, false);
        service.transition(inProgress.id(), "IN_PROGRESS");
        service.transition(resolved.id(), "IN_PROGRESS");
        service.transition(resolved.id(), "RESOLVED");
        service.transition(closed.id(), "IN_PROGRESS");
        service.transition(closed.id(), "RESOLVED");
        service.transition(closed.id(), "CLOSED");
        service.transition(cancelled.id(), "CANCELLED");
        assertThat(service.get(open.id()).status()).isEqualTo("OPEN");
    }
}
