package com.ticketflow.bookingservice.controller;

import com.ticketflow.bookingservice.dto.OrderRequest;
import com.ticketflow.bookingservice.dto.OrderResponse;
import com.ticketflow.bookingservice.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody OrderRequest request) {

        OrderResponse response = orderService.createOrder(idempotencyKey, request);

        // If it's a replay of an idempotent request, return 200 OK. If new, 201 Created.
        if (response.getMessage().contains("already processed")) {
            return ResponseEntity.status(HttpStatus.OK).body(response);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}