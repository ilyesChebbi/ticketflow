package com.ticketflow.bookingservice.exception;

import org.springframework.http.HttpStatus;

public class InsufficientSeatsException extends BookingException {

    public InsufficientSeatsException(String message) {
        super(HttpStatus.CONFLICT, "INSUFFICIENT_SEATS", message);
    }
}
