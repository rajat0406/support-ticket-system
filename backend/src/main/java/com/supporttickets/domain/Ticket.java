package com.supporttickets.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class Ticket {

    private final UUID id;
    private String title;
    private String description;
    private String priority;
    private String assignee;
    private TicketStatus status;
    private final Instant createdAt;
    private Instant updatedAt;
    private final List<Comment> comments = new ArrayList<>();

    private Ticket(
            UUID id,
            String title,
            String description,
            String priority,
            String assignee,
            TicketStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.assignee = assignee;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Ticket create(
            String title,
            String description,
            String priority,
            String assignee,
            Clock clock
    ) {
        Instant now = Instant.now(clock);
        return new Ticket(
                UUID.randomUUID(),
                requireTitle(title),
                description,
                priority,
                assignee,
                TicketStatus.OPEN,
                now,
                now
        );
    }

    static Ticket reconstitute(
            UUID id,
            String title,
            String description,
            String priority,
            String assignee,
            TicketStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new Ticket(id, requireTitle(title), description, priority, assignee, status, createdAt, updatedAt);
    }

    public void transitionTo(TicketStatus requested, Clock clock) {
        if (requested == null) {
            throw new InvalidStatusValueException(null);
        }
        if (!TicketStatusPolicy.isAllowed(status, requested)) {
            throw new IllegalStatusTransitionException(status, requested);
        }
        this.status = requested;
        this.updatedAt = Instant.now(clock);
    }

    public void updateFields(String title, String description, String priority, String assignee, Clock clock) {
        this.title = requireTitle(title);
        this.description = description;
        this.priority = priority;
        this.assignee = assignee;
        this.updatedAt = Instant.now(clock);
    }

    public Comment addComment(String body, String author, Clock clock) {
        String trimmed = body == null ? "" : body.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Comment body must not be blank.");
        }
        Comment comment = new Comment(UUID.randomUUID(), trimmed, author, Instant.now(clock));
        comments.add(comment);
        return comment;
    }

    public UUID id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public String priority() {
        return priority;
    }

    public String assignee() {
        return assignee;
    }

    public TicketStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public List<Comment> comments() {
        return Collections.unmodifiableList(comments);
    }

    private static String requireTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Title must not be blank.");
        }
        return title.trim();
    }
}
