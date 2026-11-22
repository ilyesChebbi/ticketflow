package com.ticketflow.bookingservice.repository;

import com.ticketflow.bookingservice.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    // Used for Idempotency check
    Optional<Order> findByIdempotencyKey(String idempotencyKey);
}