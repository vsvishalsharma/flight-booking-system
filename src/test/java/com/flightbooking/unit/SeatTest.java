package com.flightbooking.unit;

import com.flightbooking.common.exception.SeatNotAvailableException;
import com.flightbooking.flight.entity.FlightInstance;
import com.flightbooking.seat.entity.Seat;
import com.flightbooking.seat.entity.SeatStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

class SeatTest {

    private final FlightInstance flightInstance = new FlightInstance();

    @Test
    void newSeat_startsAvailable() {
        Seat seat = new Seat(flightInstance, "1A");

        assertThat(seat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(seat.isHoldable(LocalDateTime.now())).isTrue();
    }

    @Test
    void hold_onAvailableSeat_consumesInventory() {
        Seat seat = new Seat(flightInstance, "1A");

        boolean consumesInventory = seat.hold(LocalDateTime.now(), Duration.ofMinutes(10));

        assertThat(consumesInventory).isTrue();
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.HELD);
        assertThat(seat.getHoldExpiry()).isAfter(LocalDateTime.now());
    }

    @Test
    void hold_reclaimingExpiredHold_doesNotConsumeInventoryAgain() {
        Seat seat = new Seat(flightInstance, "1A");
        seat.hold(LocalDateTime.now().minusMinutes(20), Duration.ofMinutes(10)); // already expired

        boolean consumesInventory = seat.hold(LocalDateTime.now(), Duration.ofMinutes(10));

        assertThat(consumesInventory).isFalse();
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.HELD);
    }

    @Test
    void hold_onActivelyHeldSeat_throwsSeatNotAvailableException() {
        Seat seat = new Seat(flightInstance, "1A");
        seat.hold(LocalDateTime.now(), Duration.ofMinutes(10));

        assertThatThrownBy(() -> seat.hold(LocalDateTime.now(), Duration.ofMinutes(10)))
                .isInstanceOf(SeatNotAvailableException.class);
    }

    @Test
    void hold_onBookedSeat_throwsSeatNotAvailableException() {
        Seat seat = new Seat(flightInstance, "1A");
        seat.hold(LocalDateTime.now(), Duration.ofMinutes(10));
        seat.confirm();

        assertThatThrownBy(() -> seat.hold(LocalDateTime.now(), Duration.ofMinutes(10)))
                .isInstanceOf(SeatNotAvailableException.class);
    }

    @Test
    void confirm_onHeldSeat_transitionsToBooked() {
        Seat seat = new Seat(flightInstance, "1A");
        seat.hold(LocalDateTime.now(), Duration.ofMinutes(10));

        seat.confirm();

        assertThat(seat.getStatus()).isEqualTo(SeatStatus.BOOKED);
        assertThat(seat.getHoldExpiry()).isNull();
    }

    @Test
    void confirm_onAvailableSeat_throwsIllegalStateException() {
        Seat seat = new Seat(flightInstance, "1A");

        assertThatThrownBy(seat::confirm).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void release_onHeldSeat_transitionsToAvailable() {
        Seat seat = new Seat(flightInstance, "1A");
        seat.hold(LocalDateTime.now(), Duration.ofMinutes(10));

        seat.release();

        assertThat(seat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(seat.getHoldExpiry()).isNull();
    }

    @Test
    void release_onAlreadyAvailableSeat_throwsIllegalStateException() {
        Seat seat = new Seat(flightInstance, "1A");

        assertThatThrownBy(seat::release).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void release_onBookedSeat_throwsIllegalStateException() {
        Seat seat = new Seat(flightInstance, "1A");
        seat.hold(LocalDateTime.now(), Duration.ofMinutes(10));
        seat.confirm();

        assertThatThrownBy(seat::release).isInstanceOf(IllegalStateException.class);
    }
}
