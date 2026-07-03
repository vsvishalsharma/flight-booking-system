package com.flightbooking.booking.dto;

import jakarta.validation.constraints.*;

public record PassengerRequest(
        @NotBlank(message = "Passenger name is required")
        String name,

        @NotNull(message = "Passenger age is required")
        @Min(value = 1, message = "Age must be at least 1")
        @Max(value = 120, message = "Age must be at most 120")
        Integer age,

        @NotBlank(message = "Gender is required")
        String gender
) {
}
