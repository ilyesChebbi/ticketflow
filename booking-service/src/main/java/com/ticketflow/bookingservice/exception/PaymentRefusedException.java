package com.ticketflow.bookingservice.exception;

import org.springframework.http.HttpStatus;

public class PaymentRefusedException extends BookingException {

    public PaymentRefusedException(String message) {
        super(HttpStatus.PAYMENT_REQUIRED, "PAYMENT_REFUSED", message);
    }
}
