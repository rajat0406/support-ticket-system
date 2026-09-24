package com.supporttickets.application;

import java.util.List;

import com.supporttickets.domain.Comment;
import com.supporttickets.domain.Ticket;

final class TicketMapper {

    private TicketMapper() {
    }

    static TicketDetailDto toDetail(Ticket ticket) {
        List<CommentDto> comments = ticket.comments().stream().map(TicketMapper::toComment).toList();
        return new TicketDetailDto(
                ticket.id(),
                ticket.title(),
                ticket.description(),
                ticket.priority(),
                ticket.assignee(),
                ticket.status().name(),
                ticket.createdAt(),
                ticket.updatedAt(),
                comments
        );
    }

    static TicketSummaryDto toSummary(Ticket ticket) {
        return new TicketSummaryDto(
                ticket.id(),
                ticket.title(),
                ticket.description(),
                ticket.priority(),
                ticket.assignee(),
                ticket.status().name(),
                ticket.createdAt(),
                ticket.updatedAt()
        );
    }

    static CommentDto toComment(Comment comment) {
        return new CommentDto(comment.id(), comment.body(), comment.author(), comment.createdAt());
    }
}
