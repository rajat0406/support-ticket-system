package com.supporttickets.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.supporttickets.domain.Ticket;
import com.supporttickets.domain.TicketStatus;

public interface TicketRepository {

    Ticket save(Ticket ticket);

    Optional<Ticket> findById(UUID id);

    List<Ticket> findAllByOrderByCreatedAtDesc();

    List<Ticket> findByStatusOrderByCreatedAtDesc(TicketStatus status);

    List<Ticket> searchByKeyword(String keyword);

    List<Ticket> searchByKeywordAndStatus(String keyword, TicketStatus status);
}
