package com.ticketflow.bookingservice.exception;

import com.ticketflow.bookingservice.exception.BookingException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String TIMESTAMP = "timestamp";

    @ExceptionHandler(BookingException.class)
    public ResponseEntity<Map<String, Object>> handleBookingException(BookingException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(TIMESTAMP, Instant.now().toString());
        body.put("code", ex.getCode());
        body.put("message", ex.getMessage());
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(TIMESTAMP, Instant.now().toString());
        body.put("code", "VALIDATION_ERROR");
        body.put("message", "Donnees invalides: " + ex.getBindingResult().getAllErrors().get(0).getDefaultMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Map<String, Object>> handleMissingHeader(MissingRequestHeaderException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(TIMESTAMP, Instant.now().toString());
        body.put("code", "VALIDATION_ERROR");
        body.put("message", "En-tête manquant: " + ex.getHeaderName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleNotReadable(HttpMessageNotReadableException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(TIMESTAMP, Instant.now().toString());
        body.put("code", "VALIDATION_ERROR");
        body.put("message", "Corps de requête illisible");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleInternal(Exception ex) {
        // Log without leaking stack traces to the client
        System.err.println("Erreur interne: " + ex.getClass().getSimpleName() + " - " + ex.getMessage());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(TIMESTAMP, Instant.now().toString());
        body.put("code", "INTERNAL_ERROR");
        body.put("message", "Erreur interne du serveur");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
