package com.ticketflow.bookingservice.dto;

public class OrderResponse {
    private Long orderId;
    private String status;
    private String transactionId;
    private Double totalPrice;
    private String message;

    public OrderResponse(Long orderId, String status, String transactionId, Double totalPrice, String message) {
        this.orderId = orderId;
        this.status = status;
        this.transactionId = transactionId;
        this.totalPrice = totalPrice;
        this.message = message;
    }

    // Getters
    public Long getOrderId() { return orderId; }
    public String getStatus() { return status; }
    public String getTransactionId() { return transactionId; }
    public Double getTotalPrice() { return totalPrice; }
    public String getMessage() { return message; }
}