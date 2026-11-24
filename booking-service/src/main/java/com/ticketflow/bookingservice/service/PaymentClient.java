package com.ticketflow.bookingservice.service;

import com.ticketflow.bookingservice.exception.DependencyUnavailableException;
import com.ticketflow.bookingservice.exception.PaymentRefusedException;
import com.ticketflow.bookingservice.soap.generated.AuthorizePaymentRequest;
import com.ticketflow.bookingservice.soap.generated.AuthorizePaymentResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.client.SoapFaultClientException;

@Service
public class PaymentClient {

    private final WebServiceTemplate webServiceTemplate;

    @Value("${payment.soap.url:http://localhost:8085/ws}")
    private String paymentSoapUrl;

    public PaymentClient(WebServiceTemplate webServiceTemplate) {
        this.webServiceTemplate = webServiceTemplate;
    }

    @CircuitBreaker(name = "paymentService", fallbackMethod = "authorizePaymentFallback")
    public AuthorizePaymentResponse authorizePayment(String orderId, Double amount, String currency, String cardToken) {
        // 1. Build the request
        AuthorizePaymentRequest request = new AuthorizePaymentRequest();
        request.setOrderId(orderId);
        request.setAmount(java.math.BigDecimal.valueOf(amount));
        request.setCurrency(currency);
        request.setCardToken(cardToken);

        // 2. Send the request and get the response
        String uri = paymentSoapUrl;

        try {
            AuthorizePaymentResponse response = (AuthorizePaymentResponse) webServiceTemplate
                    .marshalSendAndReceive(uri, request);
            return response;
        } catch (SoapFaultClientException ex) {
            // SOAP fault from payment service (card refused, amount exceeds limit, etc.)
            // Business outcome: do NOT count as circuit breaker failure
            throw new PaymentRefusedException("Payment refused by PSP: " + ex.getMessage());
        }
    }

    public AuthorizePaymentResponse authorizePaymentFallback(String orderId, Double amount, String currency, String cardToken, Throwable t) {
        throw new DependencyUnavailableException("PAYMENT_UNAVAILABLE", "Service de paiement indisponible: " + t.getMessage());
    }
}
