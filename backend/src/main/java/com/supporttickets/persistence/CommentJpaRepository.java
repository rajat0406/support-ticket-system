package com.supporttickets.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentJpaRepository extends JpaRepository<CommentEntity, UUID> {

    List<CommentEntity> findByTicketIdOrderByCreatedAtAsc(UUID ticketId);
}
