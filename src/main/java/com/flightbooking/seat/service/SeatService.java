package com.flightbooking.seat.service;

import com.flightbooking.common.exception.ResourceNotFoundException;
import com.flightbooking.common.exception.SeatNotAvailableException;
import com.flightbooking.flight.repository.FlightInstanceRepository;
import com.flightbooking.seat.entity.Seat;
import com.flightbooking.seat.entity.SeatStatus;
import com.flightbooking.seat.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class SeatService {

    private static final int HOLD_DURATION_MINUTES = 10;

    private final SeatRepository seatRepository;
    private final FlightInstanceRepository flightInstanceRepository;

    /**
     * Attempts to place a hold on the requested seat.
     * Uses a pessimistic write lock to prevent concurrent double-booking of the same seat.
     * A seat with an expired hold is treated as available.
     */
    @Transactional
    public Seat holdSeat(Long seatId) {
        Seat seat = seatRepository.findByIdWithLock(seatId)
                .orElseThrow(() -> new ResourceNotFoundException("Seat not found: " + seatId));

        if (!isAvailableForHold(seat)) {
            throw new SeatNotAvailableException(
                    "Seat " + seat.getSeatNumber() + " is not available (status: " + seat.getStatus() + ")"
            );
        }

        seat.setStatus(SeatStatus.HELD);
        seat.setHoldExpiry(LocalDateTime.now().plusMinutes(HOLD_DURATION_MINUTES));

        int updated = flightInstanceRepository.decrementAvailableSeats(seat.getFlightInstance().getId());
        if (updated == 0) {
            throw new SeatNotAvailableException("No available seats on this flight");
        }

        log.debug("Seat {} held until {}", seat.getSeatNumber(), seat.getHoldExpiry());
        return seatRepository.save(seat);
    }

    /**
     * Confirms a held seat after successful payment.
     * Called only by BookingService.
     */
    @Transactional
    public Seat confirmSeat(Long seatId) {
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new ResourceNotFoundException("Seat not found: " + seatId));

        seat.setStatus(SeatStatus.BOOKED);
        seat.setHoldExpiry(null);

        log.debug("Seat {} confirmed (BOOKED)", seat.getSeatNumber());
        return seatRepository.save(seat);
    }

    /**
     * Releases a held seat back to AVAILABLE after payment failure or booking failure.
     * Called only by BookingService.
     */
    @Transactional
    public Seat releaseSeat(Long seatId) {
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new ResourceNotFoundException("Seat not found: " + seatId));

        seat.setStatus(SeatStatus.AVAILABLE);
        seat.setHoldExpiry(null);

        flightInstanceRepository.incrementAvailableSeats(seat.getFlightInstance().getId());

        log.debug("Seat {} released back to AVAILABLE", seat.getSeatNumber());
        return seatRepository.save(seat);
    }

    private boolean isAvailableForHold(Seat seat) {
        if (seat.getStatus() == SeatStatus.AVAILABLE) {
            return true;
        }
        // A held seat with an expired hold can be reclaimed
        if (seat.getStatus() == SeatStatus.HELD
                && seat.getHoldExpiry() != null
                && seat.getHoldExpiry().isBefore(LocalDateTime.now())) {
            return true;
        }
        return false;
    }
}
