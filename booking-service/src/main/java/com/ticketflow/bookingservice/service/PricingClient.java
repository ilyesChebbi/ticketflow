package com.ticketflow.bookingservice.service;

import com.ticketflow.pricing.grpc.GetPriceRequest;
import com.ticketflow.pricing.grpc.GetPriceResponse;
import com.ticketflow.pricing.grpc.PricingServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class PricingClient {

    private final PricingServiceGrpc.PricingServiceBlockingStub pricingStub;

    public PricingClient() {
        // Teammate B's pricing service port (9091)
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress("localhost", 9091)
                .usePlaintext()
                .build();

        // Deadline of 300ms as required by the project
        this.pricingStub = PricingServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(300, TimeUnit.MILLISECONDS);
    }

    public GetPriceResponse getPrice(String sessionId, String category, int quantity) {
        GetPriceRequest request = GetPriceRequest.newBuilder()
                .setSessionId(sessionId)
                .setCategory(category)
                .setQuantity(quantity)
                .setSessionDate("2026-12-15T20:30:00") // Placeholder date
                .setPromoCode("")
                .build();

        return pricingStub.getPrice(request);
    }
}