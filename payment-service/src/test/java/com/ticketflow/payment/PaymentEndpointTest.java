package com.ticketflow.payment;

import java.util.Map;
import javax.xml.transform.Source;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.ws.test.server.MockWebServiceClient;
import org.springframework.ws.test.server.RequestCreators;
import org.springframework.ws.test.server.ResponseMatchers;
import org.springframework.xml.transform.StringSource;

@SpringBootTest
class PaymentEndpointTest {

    private static final String NS = "http://ticketflow.com/payment/v1";

    @Autowired
    private ApplicationContext context;

    private MockWebServiceClient client;

    @BeforeEach
    void setUp() {
        client = MockWebServiceClient.createClient(context);
    }

    private Source authorizeRequest(String orderId, String amount, String token) {
        return new StringSource(
                "<pay:authorizePaymentRequest xmlns:pay=\"" + NS + "\">"
                        + "<pay:orderId>" + orderId + "</pay:orderId>"
                        + "<pay:amount>" + amount + "</pay:amount>"
                        + "<pay:currency>EUR</pay:currency>"
                        + "<pay:cardToken>" + token + "</pay:cardToken>"
                        + "</pay:authorizePaymentRequest>");
    }

    @Test
    void authorizeOk() throws Exception {
        client.sendRequest(RequestCreators.withPayload(authorizeRequest("o-1", "120.00", "tok_visa_ok")))
                .andExpect(ResponseMatchers.noFault())
                .andExpect(ResponseMatchers.xpath("/pay:authorizePaymentResponse/pay:status", Map.of("pay", NS))
                        .evaluatesTo("APPROVED"));
    }

    @Test
    void authorizeRefusedBadToken() throws Exception {
        client.sendRequest(RequestCreators.withPayload(authorizeRequest("o-2", "120.00", "tok_bad")))
                .andExpect(ResponseMatchers.clientOrSenderFault());
    }

    @Test
    void authorizeRefusedAmountTooHigh() throws Exception {
        client.sendRequest(RequestCreators.withPayload(authorizeRequest("o-3", "6000.00", "tok_visa_ok")))
                .andExpect(ResponseMatchers.clientOrSenderFault());
    }

    @Test
    void refundOk() throws Exception {
        Source request = new StringSource(
                "<pay:refundPaymentRequest xmlns:pay=\"" + NS + "\">"
                        + "<pay:transactionId>txn-1</pay:transactionId>"
                        + "<pay:amount>50.00</pay:amount>"
                        + "</pay:refundPaymentRequest>");
        client.sendRequest(RequestCreators.withPayload(request))
                .andExpect(ResponseMatchers.noFault())
                .andExpect(ResponseMatchers.xpath("/pay:refundPaymentResponse/pay:status", Map.of("pay", NS))
                        .evaluatesTo("REFUNDED"));
    }
}
