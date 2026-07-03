package com.flightbooking.flight.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Snapshot of a Flight for a specific travel date.
 * Once created, departure/arrival times and fare are immutable with respect to Flight changes.
 * This preserves booking consistency: existing bookings always reflect the schedule
 * that was in effect when the booking was made.
 */
@Entity
@Table(
    name = "flight_instances",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_flight_instance",
        columnNames = {"flight_id", "travel_date"}
    )
)
@Getter
@Setter
@NoArgsConstructor
public class FlightInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flight_id", nullable = false)
    private Flight flight;

    @Column(nullable = false)
    private LocalDate travelDate;

    @Column(nullable = false)
    private LocalTime departureTime;

    @Column(nullable = false)
    private LocalTime arrivalTime;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal fare;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FlightInstanceStatus status;

    @Column(nullable = false)
    private Integer availableSeats;
}
