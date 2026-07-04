package com.flightbooking.unit;

import com.flightbooking.payment.entity.Payment;
import com.flightbooking.payment.entity.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class PaymentTest {

    @Test
    void newPayment_startsPending() {
        Payment payment = new Payment(1L, BigDecimal.TEN, "key-1");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void markSuccess_fromPending_setsSuccessAndTransactionId() {
        Payment payment = new Payment(1L, BigDecimal.TEN, "key-1");

        payment.markSuccess("TXN1");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getGatewayTransactionId()).isEqualTo("TXN1");
    }

    @Test
    void markFailed_fromPending_setsFailed() {
        Payment payment = new Payment(1L, BigDecimal.TEN, "key-1");

        payment.markFailed();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void markSuccess_whenAlreadyTerminal_throwsIllegalStateException() {
        Payment payment = new Payment(1L, BigDecimal.TEN, "key-1");
        payment.markFailed();

        assertThatThrownBy(() -> payment.markSuccess("TXN1")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void markFailed_whenAlreadyTerminal_throwsIllegalStateException() {
        Payment payment = new Payment(1L, BigDecimal.TEN, "key-1");
        payment.markSuccess("TXN1");

        assertThatThrownBy(payment::markFailed).isInstanceOf(IllegalStateException.class);
    }
}
