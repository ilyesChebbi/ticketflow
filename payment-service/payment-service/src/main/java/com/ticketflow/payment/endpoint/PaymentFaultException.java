package com.ticketflow.payment.endpoint;

import org.springframework.ws.soap.server.endpoint.annotation.FaultCode;
import org.springframework.ws.soap.server.endpoint.annotation.SoapFault;

/** Translated automatically into a SOAP Fault (client fault). */
@SoapFault(faultCode = FaultCode.CLIENT)
public class PaymentFaultException extends RuntimeException {

    public PaymentFaultException(String message) {
        super(message);
    }
}
