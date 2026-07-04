package com.flightbooking.seat.controller;

import com.flightbooking.common.dto.ApiResponse;
import com.flightbooking.seat.dto.SeatResponse;
import com.flightbooking.seat.service.SeatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/flights/{flightInstanceId}/seats")
@RequiredArgsConstructor
public class SeatController {

    private final SeatService seatService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<SeatResponse>>> listSeats(@PathVariable Long flightInstanceId) {
        List<SeatResponse> seats = seatService.findByFlightInstanceId(flightInstanceId).stream()
                .map(SeatResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(seats));
    }
}
