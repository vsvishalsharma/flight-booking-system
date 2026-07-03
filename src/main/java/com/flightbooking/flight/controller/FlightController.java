package com.flightbooking.flight.controller;

import com.flightbooking.common.dto.ApiResponse;
import com.flightbooking.flight.dto.FlightInstanceResponse;
import com.flightbooking.flight.service.SearchService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/flights")
@RequiredArgsConstructor
@Validated
public class FlightController {

    private final SearchService searchService;

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<FlightInstanceResponse>>> search(
            @RequestParam @NotBlank(message = "source is required") String source,
            @RequestParam @NotBlank(message = "destination is required") String destination,
            @RequestParam @NotNull(message = "travelDate is required")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate travelDate
    ) {
        List<FlightInstanceResponse> results = searchService.search(source, destination, travelDate);
        return ResponseEntity.ok(ApiResponse.success(results));
    }
}
