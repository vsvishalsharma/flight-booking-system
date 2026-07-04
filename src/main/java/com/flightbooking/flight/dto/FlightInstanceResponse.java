package com.flightbooking.flight.dto;

import com.flightbooking.flight.entity.Flight;
import com.flightbooking.flight.entity.FlightInstance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public record FlightInstanceResponse(
        Long flightInstanceId,
        String flightNumber,
        String sourceCode,
        String sourceCity,
        String destinationCode,
        String destinationCity,
        LocalDate travelDate,
        LocalTime departureTime,
        LocalTime arrivalTime,
        BigDecimal fare,
        String status,
        Integer availableSeats
) {

    public static FlightInstanceResponse from(FlightInstance flightInstance) {
        Flight flight = flightInstance.getFlight();
        return new FlightInstanceResponse(
                flightInstance.getId(),
                flight.getFlightNumber(),
                flight.getSourceAirport().getCode(),
                flight.getSourceAirport().getCity(),
                flight.getDestinationAirport().getCode(),
                flight.getDestinationAirport().getCity(),
                flightInstance.getTravelDate(),
                flightInstance.getDepartureTime(),
                flightInstance.getArrivalTime(),
                flightInstance.getFare(),
                flightInstance.getStatus().name(),
                flightInstance.getAvailableSeats()
        );
    }
}
