package com.ticketflow.bookingservice.exception;

import org.springframework.http.HttpStatus;

public class OrderProcessingException extends BookingException {

    public OrderProcessingException(String message) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, "ORDER_PROCESSING_FAILED", message);
    }
}
