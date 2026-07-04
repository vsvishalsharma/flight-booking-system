package com.flightbooking.unit;

import com.flightbooking.payment.entity.Payment;
import com.flightbooking.payment.entity.PaymentStatus;
import com.flightbooking.payment.repository.PaymentRepository;
import com.flightbooking.payment.service.PaymentGatewaySimulator;
import com.flightbooking.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentGatewaySimulator gatewaySimulator;

    @InjectMocks private PaymentService paymentService;

    private final BigDecimal amount = BigDecimal.valueOf(5500);
    private final Long bookingId = 10L;
    private final String idempotencyKey = "test-key-001";

    @Test
    void processPayment_success_whenGatewaySucceeds() {
        when(paymentRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(gatewaySimulator.process(amount))
                .thenReturn(new PaymentGatewaySimulator.GatewayResult("TXN123", true));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            ReflectionTestUtils.setField(p, "id", 1L);
            return p;
        });

        Payment result = paymentService.processPayment(bookingId, amount, idempotencyKey);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(result.getGatewayTransactionId()).isEqualTo("TXN123");
        assertThat(result.getBookingId()).isEqualTo(bookingId);
        assertThat(result.getAmount()).isEqualByComparingTo(amount);
        assertThat(result.getIdempotencyKey()).isEqualTo(idempotencyKey);
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void processPayment_failed_whenGatewayFails() {
        when(paymentRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(gatewaySimulator.process(amount))
                .thenReturn(new PaymentGatewaySimulator.GatewayResult(null, false));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            ReflectionTestUtils.setField(p, "id", 2L);
            return p;
        });

        Payment result = paymentService.processPayment(bookingId, amount, idempotencyKey);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(result.getGatewayTransactionId()).isNull();
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void processPayment_idempotent_returnsSamePaymentOnDuplicateKey() {
        Payment existingPayment = new Payment(bookingId, amount, idempotencyKey);
        ReflectionTestUtils.setField(existingPayment, "id", 42L);
        existingPayment.markSuccess("EXISTING_TXN");

        when(paymentRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existingPayment));

        Payment result = paymentService.processPayment(bookingId, amount, idempotencyKey);

        assertThat(result.getId()).isEqualTo(42L);
        assertThat(result.getGatewayTransactionId()).isEqualTo("EXISTING_TXN");
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.SUCCESS);

        // Gateway must NOT be called again
        verify(gatewaySimulator, never()).process(any());
        // Repository must NOT save again
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void processPayment_idempotent_preservesFailedPayment() {
        Payment existingFailed = new Payment(bookingId, amount, idempotencyKey);
        ReflectionTestUtils.setField(existingFailed, "id", 43L);
        existingFailed.markFailed();

        when(paymentRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existingFailed));

        Payment result = paymentService.processPayment(bookingId, amount, idempotencyKey);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(gatewaySimulator, never()).process(any());
    }
}
