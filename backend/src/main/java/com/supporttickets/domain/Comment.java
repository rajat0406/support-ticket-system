package com.supporttickets.domain;

import java.time.Instant;
import java.util.UUID;

public final class Comment {

    private final UUID id;
    private final String body;
    private final String author;
    private final Instant createdAt;

    Comment(UUID id, String body, String author, Instant createdAt) {
        this.id = id;
        this.body = body;
        this.author = author;
        this.createdAt = createdAt;
    }

    public UUID id() {
        return id;
    }

    public String body() {
        return body;
    }

    public String author() {
        return author;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
