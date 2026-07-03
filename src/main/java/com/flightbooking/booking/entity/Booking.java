package com.flightbooking.booking.entity;

import com.flightbooking.flight.entity.FlightInstance;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Booking is the Aggregate Root (Design Decision 2).
 * BookingService orchestrates SeatService and PaymentService.
 * Neither SeatService nor PaymentService modify Booking.
 *
 * seatId is stored as a plain foreign key here (Design Decision 1).
 * Seat does NOT contain a back-reference to Booking.
 * This separation keeps inventory (Seat) and reservation (Booking) concerns distinct.
 */
@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "passenger_id", nullable = false)
    private Passenger passenger;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flight_instance_id", nullable = false)
    private FlightInstance flightInstance;

    // Booking owns the seat reference — seat does not store bookingId (Design Decision 1)
    @Column(name = "seat_id", nullable = false)
    private Long seatId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal fare;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
