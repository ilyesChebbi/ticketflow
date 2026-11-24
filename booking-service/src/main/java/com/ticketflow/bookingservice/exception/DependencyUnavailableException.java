package com.ticketflow.bookingservice.exception;

import org.springframework.http.HttpStatus;

public class DependencyUnavailableException extends BookingException {

    public DependencyUnavailableException(String code, String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, code, message);
    }
}
