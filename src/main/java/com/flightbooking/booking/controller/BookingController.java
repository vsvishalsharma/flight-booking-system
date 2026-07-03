package com.flightbooking.booking.controller;

import com.flightbooking.booking.dto.BookingRequest;
import com.flightbooking.booking.dto.BookingResponse;
import com.flightbooking.booking.service.BookingService;
import com.flightbooking.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping("/initiate")
    public ResponseEntity<ApiResponse<BookingResponse>> initiateBooking(
            @RequestBody @Valid BookingRequest request,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey
    ) {
        BookingResponse response = bookingService.initiateBooking(request, idempotencyKey);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
