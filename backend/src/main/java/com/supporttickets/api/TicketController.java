package com.supporttickets.api;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.supporttickets.application.TicketService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService tickets;

    public TicketController(TicketService tickets) {
        this.tickets = tickets;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TicketResponse create(@Valid @RequestBody CreateTicketRequest request) {
        return TicketResponse.from(tickets.create(
                request.title(),
                request.description(),
                request.priority(),
                request.assignee(),
                request.statusProvided()
        ));
    }

    @GetMapping
    public TicketListResponse list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status
    ) {
        return TicketListResponse.from(tickets.list(q, status));
    }

    @GetMapping("/{id}")
    public TicketResponse get(@PathVariable UUID id) {
        return TicketResponse.from(tickets.get(id));
    }

    @PatchMapping("/{id}")
    public TicketResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateTicketRequest request) {
        return TicketResponse.from(tickets.updateFields(
                id,
                request.title(),
                request.descriptionValue(),
                request.descriptionPresent(),
                request.priorityValue(),
                request.priorityPresent(),
                request.assigneeValue(),
                request.assigneePresent(),
                request.statusProvided()
        ));
    }

    @PostMapping("/{id}/transitions")
    public TicketResponse transition(
            @PathVariable UUID id,
            @Valid @RequestBody TransitionTicketRequest request
    ) {
        return TicketResponse.from(tickets.transition(id, request.requestedStatus()));
    }

    @PostMapping("/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse addComment(
            @PathVariable UUID id,
            @Valid @RequestBody AddCommentRequest request
    ) {
        return CommentResponse.from(tickets.addComment(id, request.body(), request.author()));
    }
}
