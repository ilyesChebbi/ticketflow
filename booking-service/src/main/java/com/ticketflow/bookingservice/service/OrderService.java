package com.ticketflow.bookingservice.service;

import com.ticketflow.bookingservice.dto.OrderRequest;
import com.ticketflow.bookingservice.dto.OrderResponse;
import com.ticketflow.bookingservice.entity.Order;
import com.ticketflow.bookingservice.exception.BookingException;
import com.ticketflow.bookingservice.exception.DependencyUnavailableException;
import com.ticketflow.bookingservice.exception.InsufficientSeatsException;
import com.ticketflow.bookingservice.exception.OrderProcessingException;
import com.ticketflow.bookingservice.exception.PaymentRefusedException;
import com.ticketflow.bookingservice.repository.OrderRepository;
import com.ticketflow.pricing.grpc.GetPriceResponse;
import com.ticketflow.seatinventory.grpc.ReserveSeatsResponse;
import com.ticketflow.seatinventory.grpc.ReserveStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ws.soap.client.SoapFaultClientException;

import java.util.Optional;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final PaymentClient paymentClient;
    private final PricingClient pricingClient;
    private final InventoryClient inventoryClient;

    public OrderService(OrderRepository orderRepository,
                        PaymentClient paymentClient,
                        PricingClient pricingClient,
                        InventoryClient inventoryClient) {
        this.orderRepository = orderRepository;
        this.paymentClient = paymentClient;
        this.pricingClient = pricingClient;
        this.inventoryClient = inventoryClient;
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

        String reservationId = order.getId().toString();

        try {
            // 3. ORCHESTRATION (SAGA PATTERN)

            // Step A: Call gRPC Seat Inventory
            ReserveSeatsResponse reserveResponse = inventoryClient.reserveSeats(
                    reservationId,
                    request.getSessionId(),
                    request.getCategory(),
                    request.getQuantity()
            );

            if (reserveResponse.getStatus() == ReserveStatus.INSUFFICIENT_SEATS) {
                throw new InsufficientSeatsException("Not enough seats available for category " + request.getCategory());
            }
            if (reserveResponse.getStatus() != ReserveStatus.RESERVED && reserveResponse.getStatus() != ReserveStatus.ALREADY_RESERVED) {
                throw new InsufficientSeatsException("Seat reservation failed: " + reserveResponse.getMessage());
            }

            // Step B: Call gRPC Pricing
            GetPriceResponse priceResponse = pricingClient.getPrice(
                    request.getSessionId(),
                    request.getCategory(),
                    request.getQuantity()
            );

            // Convert millimes to TND (Double)
            double priceInTnd = priceResponse.getTotalMillimes() / 1000.0;

            // Step C: Call SOAP Payment
            var paymentResponse = paymentClient.authorizePayment(
                    order.getId().toString(),
                    priceInTnd,
                    "TND",
                    "VALID_TOKEN"
            );

            // 4. SUCCESS -> CONFIRM ORDER
            if ("APPROVED".equals(paymentResponse.getStatus())) {
                order.setStatus("CONFIRMED");
                order.setTotalPrice(priceInTnd);
                order.setPaymentTransactionId(paymentResponse.getTransactionId());
                orderRepository.save(order);

                return new OrderResponse(order.getId(), order.getStatus(),
                        order.getPaymentTransactionId(), order.getTotalPrice(),
                        "Order confirmed successfully");
            } else {
                throw new PaymentRefusedException("Payment refused by PSP: status=" + paymentResponse.getStatus());
            }

        } catch (SoapFaultClientException ex) {
            // Compensation: release seats if they were reserved
            compensate(reservationId);
            markFailed(order);
            throw new PaymentRefusedException("Payment refused by PSP: " + ex.getMessage());
        } catch (BookingException ex) {
            // Typed business/dependency exceptions pass through unchanged after compensation
            compensate(reservationId);
            markFailed(order);
            throw ex;
        } catch (Exception ex) {
            // Wrap any other exception
            compensate(reservationId);
            markFailed(order);
            throw new OrderProcessingException("Order processing failed: " + ex.getMessage());
        }
    }

    private void compensate(String reservationId) {
        try {
            inventoryClient.releaseSeats(reservationId);
        } catch (Exception ex) {
            System.err.println("Failed to release seats for order " + reservationId + ": " + ex.getMessage());
        }
    }

    private void markFailed(Order order) {
        order.setStatus("FAILED");
        orderRepository.save(order);
    }
}
