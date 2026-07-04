package com.flightbooking.unit;

import com.flightbooking.booking.entity.Booking;
import com.flightbooking.booking.entity.BookingStatus;
import com.flightbooking.booking.entity.Passenger;
import com.flightbooking.booking.entity.User;
import com.flightbooking.flight.entity.FlightInstance;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class BookingTest {

    @Test
    void newBooking_startsPendingWithTimestamp() {
        Booking booking = newPendingBooking();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(booking.getCreatedAt()).isNotNull();
    }

    @Test
    void confirm_fromPending_transitionsToConfirmed() {
        Booking booking = newPendingBooking();

        booking.confirm();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void fail_fromPending_transitionsToFailed() {
        Booking booking = newPendingBooking();

        booking.fail();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.FAILED);
    }

    @Test
    void confirm_whenAlreadyConfirmed_throwsIllegalStateException() {
        Booking booking = newPendingBooking();
        booking.confirm();

        assertThatThrownBy(booking::confirm).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void fail_whenAlreadyFailed_throwsIllegalStateException() {
        Booking booking = newPendingBooking();
        booking.fail();

        assertThatThrownBy(booking::fail).isInstanceOf(IllegalStateException.class);
    }

    private Booking newPendingBooking() {
        return new Booking(new User(), new Passenger("Vishal", 30, "Male"), new FlightInstance(), 1L, BigDecimal.TEN);
    }
}
