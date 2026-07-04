package com.flightbooking.seat.dto;

import com.flightbooking.seat.entity.Seat;

public record SeatResponse(Long seatId, String seatNumber, String status) {

    public static SeatResponse from(Seat seat) {
        return new SeatResponse(seat.getId(), seat.getSeatNumber(), seat.getStatus().name());
    }
}
