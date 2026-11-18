package com.ticketflow.payment.endpoint;

import com.ticketflow.payment.generated.AuthorizePaymentRequest;
import com.ticketflow.payment.generated.AuthorizePaymentResponse;
import com.ticketflow.payment.generated.RefundPaymentRequest;
import com.ticketflow.payment.generated.RefundPaymentResponse;
import java.math.BigDecimal;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

@Endpoint
public class PaymentEndpoint {

    private static final Logger log = LoggerFactory.getLogger(PaymentEndpoint.class);
    private static final String NS = "http://ticketflow.com/payment/v1";
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("5000");

    @PayloadRoot(namespace = NS, localPart = "authorizePaymentRequest")
    @ResponsePayload
    public AuthorizePaymentResponse authorize(@RequestPayload AuthorizePaymentRequest request) {
        log.info("authorizePayment orderId={} amount={} currency={}",
                request.getOrderId(), request.getAmount(), request.getCurrency());

        if (request.getAmount() == null || request.getAmount().compareTo(MAX_AMOUNT) > 0) {
            throw new PaymentFaultException("REFUSED: amount exceeds limit");
        }
        if (request.getCardToken() == null || !request.getCardToken().endsWith("_ok")) {
            throw new PaymentFaultException("REFUSED: invalid card token");
        }

        AuthorizePaymentResponse response = new AuthorizePaymentResponse();
        response.setTransactionId("txn-" + UUID.randomUUID());
        response.setStatus("AUTHORIZED");
        return response;
    }

    @PayloadRoot(namespace = NS, localPart = "refundPaymentRequest")
    @ResponsePayload
    public RefundPaymentResponse refund(@RequestPayload RefundPaymentRequest request) {
        log.info("refundPayment transactionId={} amount={}", request.getTransactionId(), request.getAmount());

        RefundPaymentResponse response = new RefundPaymentResponse();
        response.setRefundId("rf-" + UUID.randomUUID());
        response.setStatus("REFUNDED");
        return response;
    }
}
