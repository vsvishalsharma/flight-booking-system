package com.flightbooking.seat.entity;

import com.flightbooking.flight.entity.FlightInstance;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Represents an individual seat on a FlightInstance.
 * Seat owns inventory state (AVAILABLE / HELD / BOOKED).
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
@Setter
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
}
