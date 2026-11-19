package com.ticketflow.eventcatalog.repository;

import com.ticketflow.eventcatalog.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {
}