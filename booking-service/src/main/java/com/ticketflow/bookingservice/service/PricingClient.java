package com.ticketflow.bookingservice.service;

import com.ticketflow.bookingservice.exception.DependencyUnavailableException;
import com.ticketflow.pricing.grpc.GetPriceRequest;
import com.ticketflow.pricing.grpc.GetPriceResponse;
import com.ticketflow.pricing.grpc.PricingServiceGrpc;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class PricingClient {

    private final PricingServiceGrpc.PricingServiceBlockingStub pricingStub;

    public PricingClient(@Value("${grpc.pricing.host:localhost}") String host,
                         @Value("${grpc.pricing.port:9091}") int port) {
        // Teammate B's pricing service port (9091)
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(host, port)
                .usePlaintext()
                .build();

        // No deadline on the stub: applied per call to avoid expiry after startup
        this.pricingStub = PricingServiceGrpc.newBlockingStub(channel);
    }

    @CircuitBreaker(name = "pricingService", fallbackMethod = "getPriceFallback")
    public GetPriceResponse getPrice(String sessionId, String category, int quantity) {
        GetPriceRequest request = GetPriceRequest.newBuilder()
                .setSessionId(sessionId)
                .setCategory(category)
                .setQuantity(quantity)
                .setSessionDate("2026-12-15T20:30:00") // Placeholder date
                .setPromoCode("")
                .build();

        return pricingStub.withDeadlineAfter(300, TimeUnit.MILLISECONDS).getPrice(request);
    }

    public GetPriceResponse getPriceFallback(String sessionId, String category, int quantity, Throwable t) {
        throw new DependencyUnavailableException("PRICING_UNAVAILABLE", "Service de tarification indisponible: " + t.getMessage());
    }
}
