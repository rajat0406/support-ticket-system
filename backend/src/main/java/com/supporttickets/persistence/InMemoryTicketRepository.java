package com.supporttickets.persistence;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

import com.supporttickets.application.TicketRepository;
import com.supporttickets.domain.Ticket;
import com.supporttickets.domain.TicketStatus;

@Repository
public class InMemoryTicketRepository implements TicketRepository {

    private final ConcurrentHashMap<UUID, Ticket> tickets = new ConcurrentHashMap<>();

    @Override
    public Ticket save(Ticket ticket) {
        tickets.put(ticket.id(), ticket);
        return ticket;
    }

    @Override
    public Optional<Ticket> findById(UUID id) {
        return Optional.ofNullable(tickets.get(id));
    }

    @Override
    public List<Ticket> findAllByOrderByCreatedAtDesc() {
        return tickets.values().stream().sorted(newestFirst()).toList();
    }

    @Override
    public List<Ticket> findByStatusOrderByCreatedAtDesc(TicketStatus status) {
        return tickets.values().stream()
                .filter(ticket -> ticket.status() == status)
                .sorted(newestFirst())
                .toList();
    }

    @Override
    public List<Ticket> searchByKeyword(String keyword) {
        return tickets.values().stream()
                .filter(ticket -> matchesKeyword(ticket, keyword))
                .sorted(newestFirst())
                .toList();
    }

    @Override
    public List<Ticket> searchByKeywordAndStatus(String keyword, TicketStatus status) {
        return tickets.values().stream()
                .filter(ticket -> ticket.status() == status)
                .filter(ticket -> matchesKeyword(ticket, keyword))
                .sorted(newestFirst())
                .toList();
    }

    public void clear() {
        tickets.clear();
    }

    private static Comparator<Ticket> newestFirst() {
        return Comparator.comparing(Ticket::createdAt).reversed();
    }

    /**
     * Documented search: case-insensitive partial match on title or description.
     * Product may later replace this with exact or full-text search.
     */
    private static boolean matchesKeyword(Ticket ticket, String keyword) {
        String needle = keyword.toLowerCase(Locale.ROOT);
        if (ticket.title().toLowerCase(Locale.ROOT).contains(needle)) {
            return true;
        }
        String description = ticket.description();
        return description != null && description.toLowerCase(Locale.ROOT).contains(needle);
    }
}
