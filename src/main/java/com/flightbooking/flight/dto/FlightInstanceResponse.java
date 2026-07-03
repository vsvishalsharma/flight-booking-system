package com.flightbooking.flight.dto;

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
}
