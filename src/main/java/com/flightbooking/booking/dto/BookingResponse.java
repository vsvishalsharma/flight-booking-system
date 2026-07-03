package com.flightbooking.booking.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record BookingResponse(
        Long bookingId,
        String bookingStatus,
        Long userId,
        PassengerInfo passenger,
        FlightInfo flight,
        String seatNumber,
        BigDecimal fare,
        PaymentInfo payment,
        LocalDateTime createdAt
) {

    public record PassengerInfo(Long id, String name, Integer age, String gender) {
    }

    public record FlightInfo(
            Long flightInstanceId,
            String flightNumber,
            String sourceCode,
            String sourceCity,
            String destinationCode,
            String destinationCity,
            LocalDate travelDate,
            LocalTime departureTime,
            LocalTime arrivalTime
    ) {
    }

    public record PaymentInfo(Long paymentId, String status, String gatewayTransactionId) {
    }
}
