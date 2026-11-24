package com.ticketflow.bookingservice.service;

import com.ticketflow.bookingservice.exception.DependencyUnavailableException;
import com.ticketflow.seatinventory.grpc.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class InventoryClient {

    private final SeatInventoryServiceGrpc.SeatInventoryServiceBlockingStub inventoryStub;

    public InventoryClient(@Value("${grpc.seat-inventory.host:localhost}") String host,
                           @Value("${grpc.seat-inventory.port:9090}") int port) {
        // Teammate B's inventory service port (9090)
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(host, port)
                .usePlaintext()
                .build();

        // No deadline on the stub: applied per call to avoid expiry after startup
        this.inventoryStub = SeatInventoryServiceGrpc.newBlockingStub(channel);
    }

    @CircuitBreaker(name = "inventoryService", fallbackMethod = "reserveSeatsFallback")
    public ReserveSeatsResponse reserveSeats(String reservationId, String sessionId, String category, int quantity) {
        ReserveSeatsRequest request = ReserveSeatsRequest.newBuilder()
                .setReservationId(reservationId)
                .setSessionId(sessionId)
                .setCategory(category)
                .setQuantity(quantity)
                .build();

        return inventoryStub.withDeadlineAfter(500, TimeUnit.MILLISECONDS).reserveSeats(request);
    }

    public ReserveSeatsResponse reserveSeatsFallback(String reservationId, String sessionId, String category, int quantity, Throwable t) {
        throw new DependencyUnavailableException("INVENTORY_UNAVAILABLE", "Service d'inventaire indisponible: " + t.getMessage());
    }

    public void releaseSeats(String reservationId) {
        ReleaseSeatsRequest request = ReleaseSeatsRequest.newBuilder()
                .setReservationId(reservationId)
                .build();

        inventoryStub.withDeadlineAfter(500, TimeUnit.MILLISECONDS).releaseSeats(request);
    }
}
