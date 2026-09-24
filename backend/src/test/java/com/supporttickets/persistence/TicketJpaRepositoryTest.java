package com.supporttickets.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class TicketJpaRepositoryTest {

    private static final Instant T0 = Instant.parse("2026-09-25T01:00:00Z");

    @Autowired
    private TicketJpaRepository tickets;

    @Autowired
    private CommentJpaRepository comments;

    @Test
    void lookupById() {
        TicketEntity saved = tickets.save(ticket("Login page crashes", "Crash on submit", "OPEN", T0));
        assertThat(tickets.findById(saved.getId())).isPresent();
        assertThat(tickets.findById(UUID.fromString("00000000-0000-0000-0000-000000000099"))).isEmpty();
    }

    @Test
    void listingReturnsAllTickets() {
        seedFive();
        assertThat(tickets.findAllByOrderByCreatedAtDesc()).hasSize(5);
    }

    @Test
    void keywordSearchIsCaseInsensitivePartialMatchOnTitleAndDescription() {
        seedFive();
        List<TicketEntity> loginHits = tickets.searchByKeyword("Login");
        assertThat(loginHits).extracting(TicketEntity::getTitle)
                .containsExactlyInAnyOrder("Login page crashes", "Login logo missing");
        assertThat(tickets.searchByKeyword("billing")).isEmpty();
        assertThat(tickets.searchByKeyword("EMAIL")).extracting(TicketEntity::getTitle)
                .containsExactly("Password reset broken");
    }

    @Test
    void statusFilterReturnsOnlyMatchingStatus() {
        seedFive();
        assertThat(tickets.findByStatusOrderByCreatedAtDesc("CLOSED"))
                .extracting(TicketEntity::getTitle)
                .containsExactly("Old SSO issue");
        assertThat(tickets.findByStatusOrderByCreatedAtDesc("OPEN")).hasSize(1);
    }

    @Test
    void keywordAndStatusCombineWithAnd() {
        seedFive();
        assertThat(tickets.searchByKeywordAndStatus("Login", "OPEN"))
                .extracting(TicketEntity::getTitle)
                .containsExactly("Login page crashes");
        assertThat(tickets.searchByKeywordAndStatus("Login", "CLOSED")).isEmpty();
    }

    @Test
    void commentsPersistAndLoadInCreatedAtOrder() {
        TicketEntity ticket = tickets.save(ticket("Login page crashes", "Crash", "OPEN", T0));
        comments.save(new CommentEntity(
                UUID.randomUUID(), ticket, "Second", null, Instant.parse("2026-09-25T01:02:00Z")));
        comments.save(new CommentEntity(
                UUID.randomUUID(), ticket, "First", "alex", Instant.parse("2026-09-25T01:01:00Z")));
        List<CommentEntity> loaded = comments.findByTicketIdOrderByCreatedAtAsc(ticket.getId());
        assertThat(loaded).extracting(CommentEntity::getBody).containsExactly("First", "Second");
        assertThat(loaded.getFirst().getAuthor()).isEqualTo("alex");
    }

    private void seedFive() {
        tickets.save(ticket("Login page crashes", "Crash on submit", "OPEN", Instant.parse("2026-09-25T01:00:00Z")));
        tickets.save(ticket("Password reset broken", "Email never arrives", "IN_PROGRESS", Instant.parse("2026-09-25T01:01:00Z")));
        tickets.save(ticket("Login logo missing", "Asset 404", "RESOLVED", Instant.parse("2026-09-25T01:02:00Z")));
        tickets.save(ticket("Old SSO issue", "Superseded", "CLOSED", Instant.parse("2026-09-25T01:03:00Z")));
        tickets.save(ticket("Duplicate signup", "Created twice", "CANCELLED", Instant.parse("2026-09-25T01:04:00Z")));
    }

    private static TicketEntity ticket(String title, String description, String status, Instant createdAt) {
        return new TicketEntity(
                UUID.randomUUID(),
                title,
                description,
                null,
                null,
                status,
                createdAt,
                createdAt
        );
    }
}
