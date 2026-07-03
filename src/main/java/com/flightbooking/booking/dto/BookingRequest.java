package com.flightbooking.booking.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record BookingRequest(
        @NotNull(message = "userId is required")
        Long userId,

        @NotNull(message = "flightInstanceId is required")
        Long flightInstanceId,

        @NotNull(message = "seatId is required")
        Long seatId,

        @NotNull(message = "passenger is required")
        @Valid
        PassengerRequest passenger
) {
}
