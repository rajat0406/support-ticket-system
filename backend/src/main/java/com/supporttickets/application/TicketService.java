package com.supporttickets.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.supporttickets.domain.InvalidRequestException;
import com.supporttickets.domain.StatusNotUpdatableException;
import com.supporttickets.domain.Ticket;
import com.supporttickets.domain.TicketNotFoundException;
import com.supporttickets.domain.TicketStatus;

@Service
public class TicketService {

    private final TicketRepository tickets;
    private final Clock clock;

    public TicketService(TicketRepository tickets, Clock clock) {
        this.tickets = tickets;
        this.clock = clock;
    }

    @Transactional
    public TicketDetailDto create(
            String title,
            String description,
            String priority,
            String assignee,
            boolean statusProvided
    ) {
        if (statusProvided) {
            throw new StatusNotUpdatableException();
        }
        requireTitle(title);
        Ticket ticket = Ticket.create(title, description, priority, assignee, clock);
        return TicketMapper.toDetail(tickets.save(ticket));
    }

    @Transactional(readOnly = true)
    public TicketDetailDto get(UUID id) {
        return TicketMapper.toDetail(requireTicket(id));
    }

    @Transactional(readOnly = true)
    public List<TicketSummaryDto> list(String q, String statusLabel) {
        boolean hasKeyword = q != null && !q.isBlank();
        TicketStatus status = parseOptionalStatusFilter(statusLabel);
        List<Ticket> found;
        if (hasKeyword && status != null) {
            found = tickets.searchByKeywordAndStatus(q.trim(), status);
        } else if (hasKeyword) {
            found = tickets.searchByKeyword(q.trim());
        } else if (status != null) {
            found = tickets.findByStatusOrderByCreatedAtDesc(status);
        } else {
            found = tickets.findAllByOrderByCreatedAtDesc();
        }
        return found.stream().map(TicketMapper::toSummary).toList();
    }

    @Transactional
    public TicketDetailDto updateFields(
            UUID id,
            String title,
            String description,
            boolean descriptionPresent,
            String priority,
            boolean priorityPresent,
            String assignee,
            boolean assigneePresent,
            boolean statusProvided
    ) {
        if (statusProvided) {
            throw new StatusNotUpdatableException();
        }
        requireTitle(title);
        Ticket ticket = requireTicket(id);
        ticket.updateFields(
                title,
                descriptionPresent ? description : ticket.description(),
                priorityPresent ? priority : ticket.priority(),
                assigneePresent ? assignee : ticket.assignee(),
                clock
        );
        return TicketMapper.toDetail(tickets.save(ticket));
    }

    @Transactional
    public TicketDetailDto transition(UUID id, String requestedStatusLabel) {
        if (requestedStatusLabel == null || requestedStatusLabel.isBlank()) {
            throw new InvalidRequestException("Requested status is required.");
        }
        Ticket ticket = requireTicket(id);
        TicketStatus requested = TicketStatus.fromLabel(requestedStatusLabel);
        ticket.transitionTo(requested, clock);
        return TicketMapper.toDetail(tickets.save(ticket));
    }

    @Transactional
    public CommentDto addComment(UUID id, String body, String author) {
        if (body == null || body.isBlank()) {
            throw new InvalidRequestException("Comment body must not be blank.");
        }
        Ticket ticket = requireTicket(id);
        var comment = ticket.addComment(body, author, clock);
        tickets.save(ticket);
        return TicketMapper.toComment(comment);
    }

    private Ticket requireTicket(UUID id) {
        return tickets.findById(id).orElseThrow(() -> new TicketNotFoundException(id));
    }

    private static TicketStatus parseOptionalStatusFilter(String statusLabel) {
        if (statusLabel == null) {
            return null;
        }
        return TicketStatus.fromLabel(statusLabel);
    }

    private static void requireTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new InvalidRequestException("Title must not be blank.");
        }
    }
}
