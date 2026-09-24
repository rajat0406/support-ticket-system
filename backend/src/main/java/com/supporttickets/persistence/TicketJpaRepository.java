package com.supporttickets.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketJpaRepository extends JpaRepository<TicketEntity, UUID> {

    List<TicketEntity> findAllByOrderByCreatedAtDesc();

    List<TicketEntity> findByStatusOrderByCreatedAtDesc(String status);

    @Query("""
            select t from TicketEntity t
            where lower(t.title) like lower(concat('%', :keyword, '%'))
               or lower(coalesce(t.description, '')) like lower(concat('%', :keyword, '%'))
            order by t.createdAt desc
            """)
    List<TicketEntity> searchByKeyword(@Param("keyword") String keyword);

    @Query("""
            select t from TicketEntity t
            where t.status = :status
              and (lower(t.title) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(t.description, '')) like lower(concat('%', :keyword, '%')))
            order by t.createdAt desc
            """)
    List<TicketEntity> searchByKeywordAndStatus(
            @Param("keyword") String keyword,
            @Param("status") String status
    );
}
