package com.supporttickets.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "comment", indexes = {
        @Index(name = "idx_comment_ticket_id", columnList = "ticket_id"),
        @Index(name = "idx_comment_ticket_created", columnList = "ticket_id, created_at")
})
public class CommentEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private TicketEntity ticket;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    private String author;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CommentEntity() {
    }

    public CommentEntity(UUID id, TicketEntity ticket, String body, String author, Instant createdAt) {
        this.id = id;
        this.ticket = ticket;
        this.body = body;
        this.author = author;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public TicketEntity getTicket() {
        return ticket;
    }

    public String getBody() {
        return body;
    }

    public String getAuthor() {
        return author;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
