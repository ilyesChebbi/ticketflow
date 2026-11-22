package com.ticketflow.bookingservice.service;

import com.ticketflow.seatinventory.grpc.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class InventoryClient {

    private final SeatInventoryServiceGrpc.SeatInventoryServiceBlockingStub inventoryStub;

    public InventoryClient() {
        // Teammate B's inventory service port (9090)
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress("localhost", 9090)
                .usePlaintext()
                .build();

        // Deadline of 500ms as specified in the PDF
        this.inventoryStub = SeatInventoryServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(500, TimeUnit.MILLISECONDS);
    }

    public ReserveSeatsResponse reserveSeats(String reservationId, String sessionId, String category, int quantity) {
        ReserveSeatsRequest request = ReserveSeatsRequest.newBuilder()
                .setReservationId(reservationId)
                .setSessionId(sessionId)
                .setCategory(category)
                .setQuantity(quantity)
                .build();

        return inventoryStub.reserveSeats(request);
    }

    public void releaseSeats(String reservationId) {
        ReleaseSeatsRequest request = ReleaseSeatsRequest.newBuilder()
                .setReservationId(reservationId)
                .build();

        inventoryStub.releaseSeats(request);
    }
}