package com.flightbooking.payment.service;

import com.flightbooking.payment.entity.Payment;
import com.flightbooking.payment.entity.PaymentStatus;
import com.flightbooking.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentGatewaySimulator gatewaySimulator;

    /**
     * Processes a payment for a booking.
     *
     * Idempotent: if a payment with the same idempotencyKey already exists,
     * returns the existing record without charging again.
     *
     * Payment is synchronous — no webhooks or callbacks.
     */
    @Transactional
    public Payment processPayment(Long bookingId, BigDecimal amount, String idempotencyKey) {
        return paymentRepository.findByIdempotencyKey(idempotencyKey)
                .orElseGet(() -> chargeAndPersist(bookingId, amount, idempotencyKey));
    }

    private Payment chargeAndPersist(Long bookingId, BigDecimal amount, String idempotencyKey) {
        Payment payment = new Payment();
        payment.setBookingId(bookingId);
        payment.setAmount(amount);
        payment.setIdempotencyKey(idempotencyKey);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setCreatedAt(LocalDateTime.now());

        PaymentGatewaySimulator.GatewayResult result = gatewaySimulator.process(amount);
        log.debug("Gateway response for booking {}: success={}", bookingId, result.success());

        if (result.success()) {
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setGatewayTransactionId(result.transactionId());
        } else {
            payment.setStatus(PaymentStatus.FAILED);
        }

        return paymentRepository.save(payment);
    }
}
