package com.ticketflow.bookingservice.service;

import com.ticketflow.bookingservice.soap.generated.AuthorizePaymentRequest;
import com.ticketflow.bookingservice.soap.generated.AuthorizePaymentResponse;
import org.springframework.stereotype.Service;
import org.springframework.ws.client.core.WebServiceTemplate;

@Service
public class PaymentClient {

    private final WebServiceTemplate webServiceTemplate;

    public PaymentClient(WebServiceTemplate webServiceTemplate) {
        this.webServiceTemplate = webServiceTemplate;
    }

    public AuthorizePaymentResponse authorizePayment(String orderId, Double amount, String currency, String cardToken) {
        // 1. Build the request
        AuthorizePaymentRequest request = new AuthorizePaymentRequest();
        request.setOrderId(orderId);
        request.setAmount(java.math.BigDecimal.valueOf(amount));
        request.setCurrency(currency);
        request.setCardToken(cardToken);

        // 2. Send the request and get the response
        // Note: The URL here is a placeholder until Teammate C gives us the real endpoint
        String uri = "http://localhost:8085/ws";

        AuthorizePaymentResponse response = (AuthorizePaymentResponse) webServiceTemplate
                .marshalSendAndReceive(uri, request);

        return response;
    }
}