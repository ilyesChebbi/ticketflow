package com.ticketflow.bookingservice.service;

import com.ticketflow.bookingservice.dto.OrderRequest;
import com.ticketflow.bookingservice.dto.OrderResponse;
import com.ticketflow.bookingservice.entity.Order;
import com.ticketflow.bookingservice.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    // TODO: Inject gRPC clients and SOAP client here later

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional
    public OrderResponse createOrder(String idempotencyKey, OrderRequest request) {
        // 1. IDEMPOTENCY CHECK
        Optional<Order> existingOrder = orderRepository.findByIdempotencyKey(idempotencyKey);
        if (existingOrder.isPresent()) {
            Order order = existingOrder.get();
            return new OrderResponse(order.getId(), order.getStatus(),
                    order.getPaymentTransactionId(), order.getTotalPrice(),
                    "Order already processed (Idempotent response)");
        }

        // 2. CREATE PENDING ORDER
        Order order = new Order();
        order.setIdempotencyKey(idempotencyKey);
        order.setSessionId(request.getSessionId());
        order.setCategory(request.getCategory());
        order.setQuantity(request.getQuantity());
        order.setStatus("PENDING");
        order = orderRepository.save(order);

        try {
            // 3. ORCHESTRATION (SAGA PATTERN)

            // Step A: Call Teammate B (gRPC Seat Inventory - ReserveSeats)
            // boolean reserved = inventoryClient.reserveSeats(request.getSessionId(), request.getCategory(), request.getQuantity(), order.getId().toString());

            // Step B: Call Teammate B (gRPC Pricing - GetPrice)
            // double price = pricingClient.getPrice(request.getSessionId(), request.getCategory(), request.getQuantity());

            // Step C: Call Teammate C (SOAP Payment - authorizePayment)
            // String transactionId = paymentClient.authorizePayment(order.getId().toString(), price, "EUR", "VALID_TOKEN");

            // 4. SUCCESS -> CONFIRM ORDER
            order.setStatus("CONFIRMED");
            // order.setTotalPrice(price);
            // order.setPaymentTransactionId(transactionId);
            orderRepository.save(order);

            return new OrderResponse(order.getId(), order.getStatus(),
                    order.getPaymentTransactionId(), order.getTotalPrice(),
                    "Order confirmed successfully");

        } catch (Exception e) {
            // 5. COMPENSATION (ROLLBACK)
            // If payment fails, we must release the seats we just reserved!
            // inventoryClient.releaseSeats(order.getId().toString());

            order.setStatus("FAILED");
            orderRepository.save(order);
            throw new RuntimeException("Order processing failed: " + e.getMessage());
        }
    }
}