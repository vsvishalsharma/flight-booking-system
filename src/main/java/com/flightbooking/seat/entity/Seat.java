package com.flightbooking.seat.entity;

import com.flightbooking.common.exception.SeatNotAvailableException;
import com.flightbooking.flight.entity.FlightInstance;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Represents an individual seat on a FlightInstance.
 * Seat owns its inventory state machine (AVAILABLE -> HELD -> BOOKED, or HELD -> AVAILABLE).
 * The booking reference is NOT stored here — Booking owns the seatId (Design Decision 1).
 */
@Entity
@Table(
    name = "seats",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_seat",
        columnNames = {"flight_instance_id", "seat_number"}
    )
)
@Getter
@NoArgsConstructor
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flight_instance_id", nullable = false)
    private FlightInstance flightInstance;

    @Column(nullable = false, length = 10)
    private String seatNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SeatStatus status;

    private LocalDateTime holdExpiry;

    public Seat(FlightInstance flightInstance, String seatNumber) {
        this.flightInstance = flightInstance;
        this.seatNumber = seatNumber;
        this.status = SeatStatus.AVAILABLE;
    }

    /**
     * A seat can be held if it is free, or if it is HELD but the previous hold has expired.
     */
    public boolean isHoldable(LocalDateTime now) {
        if (status == SeatStatus.AVAILABLE) {
            return true;
        }
        return status == SeatStatus.HELD && holdExpiry != null && holdExpiry.isBefore(now);
    }

    /**
     * Places a hold on this seat.
     *
     * @return true if this hold consumes a fresh unit of seat inventory (the seat was AVAILABLE),
     *         false if it merely reclaims an already-expired hold. The denormalized
     *         FlightInstance.availableSeats counter was already decremented when this seat was
     *         first held, so reclaiming it must not decrement it a second time.
     */
    public boolean hold(LocalDateTime now, Duration holdDuration) {
        if (!isHoldable(now)) {
            throw new SeatNotAvailableException(
                    "Seat " + seatNumber + " is not available (status: " + status + ")"
            );
        }
        boolean consumesInventory = status == SeatStatus.AVAILABLE;
        status = SeatStatus.HELD;
        holdExpiry = now.plus(holdDuration);
        return consumesInventory;
    }

    public void confirm() {
        if (status != SeatStatus.HELD) {
            throw new IllegalStateException("Cannot confirm seat " + seatNumber + " from status " + status);
        }
        status = SeatStatus.BOOKED;
        holdExpiry = null;
    }

    public void release() {
        if (status != SeatStatus.HELD) {
            throw new IllegalStateException("Cannot release seat " + seatNumber + " from status " + status);
        }
        status = SeatStatus.AVAILABLE;
        holdExpiry = null;
    }
}
