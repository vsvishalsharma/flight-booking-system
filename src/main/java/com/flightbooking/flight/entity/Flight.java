package com.flightbooking.flight.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalTime;

@Entity
@Table(name = "flights")
@Getter
@Setter
@NoArgsConstructor
public class Flight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 20)
    private String flightNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_airport_id", nullable = false)
    private Airport sourceAirport;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_airport_id", nullable = false)
    private Airport destinationAirport;

    @Column(nullable = false)
    private LocalTime defaultDepartureTime;

    @Column(nullable = false)
    private LocalTime defaultArrivalTime;

    // defaultFare is copied into FlightInstance at creation time (snapshot pattern).
    // It is added to Flight to support lazy FlightInstance generation — see DESIGN.md.
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal defaultFare;

    @Column(nullable = false)
    private Integer totalSeats;
}
