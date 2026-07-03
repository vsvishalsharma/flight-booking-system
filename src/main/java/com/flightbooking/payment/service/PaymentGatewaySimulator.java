package com.flightbooking.payment.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Simulates an external payment gateway.
 * In production this would be replaced with an actual gateway client (e.g. Razorpay, Stripe).
 *
 * Always returns SUCCESS in this implementation.
 * Tests mock this component to simulate gateway failures.
 */
@Component
public class PaymentGatewaySimulator {

    public GatewayResult process(BigDecimal amount) {
        String transactionId = "TXN" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        return new GatewayResult(transactionId, true);
    }

    public record GatewayResult(String transactionId, boolean success) {
    }
}
