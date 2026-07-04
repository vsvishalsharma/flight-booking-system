package com.flightbooking.payment.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long bookingId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(length = 100)
    private String gatewayTransactionId;

    // Unique key ensures idempotency: duplicate requests return the same Payment record.
    @Column(unique = true, nullable = false, length = 255)
    private String idempotencyKey;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public Payment(Long bookingId, BigDecimal amount, String idempotencyKey) {
        this.bookingId = bookingId;
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
        this.status = PaymentStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public void markSuccess(String gatewayTransactionId) {
        if (status != PaymentStatus.PENDING) {
            throw new IllegalStateException("Cannot mark payment " + id + " successful from status " + status);
        }
        this.status = PaymentStatus.SUCCESS;
        this.gatewayTransactionId = gatewayTransactionId;
    }

    public void markFailed() {
        if (status != PaymentStatus.PENDING) {
            throw new IllegalStateException("Cannot mark payment " + id + " failed from status " + status);
        }
        this.status = PaymentStatus.FAILED;
    }
}
