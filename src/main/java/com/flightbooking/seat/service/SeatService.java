package com.flightbooking.seat.service;

import com.flightbooking.common.exception.ResourceNotFoundException;
import com.flightbooking.common.exception.SeatNotAvailableException;
import com.flightbooking.flight.entity.FlightInstance;
import com.flightbooking.flight.repository.FlightInstanceRepository;
import com.flightbooking.seat.entity.Seat;
import com.flightbooking.seat.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SeatService {

    private static final Duration HOLD_DURATION = Duration.ofMinutes(10);
    private static final String[] SEAT_COLUMNS = {"A", "B", "C", "D", "E", "F"};

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

        boolean consumesInventory = seat.hold(LocalDateTime.now(), HOLD_DURATION);

        // Only decrement when the seat was truly AVAILABLE. Reclaiming an already-expired
        // hold must not decrement again — the original hold already consumed this unit of
        // inventory and it was never restored (no background expiry job exists).
        if (consumesInventory) {
            int updated = flightInstanceRepository.decrementAvailableSeats(seat.getFlightInstance().getId());
            if (updated == 0) {
                throw new SeatNotAvailableException("No available seats on this flight");
            }
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

        seat.confirm();

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

        seat.release();
        flightInstanceRepository.incrementAvailableSeats(seat.getFlightInstance().getId());

        log.debug("Seat {} released back to AVAILABLE", seat.getSeatNumber());
        return seatRepository.save(seat);
    }

    /**
     * Generates and persists the seat inventory for a newly created FlightInstance.
     * Owned by the seat module — callers (e.g. SearchService) must not touch SeatRepository directly.
     */
    @Transactional
    public List<Seat> generateSeats(FlightInstance instance, int totalSeats) {
        List<Seat> seats = new ArrayList<>(totalSeats);
        int created = 0;
        int row = 1;

        while (created < totalSeats) {
            for (String column : SEAT_COLUMNS) {
                if (created >= totalSeats) {
                    break;
                }
                seats.add(new Seat(instance, row + column));
                created++;
            }
            row++;
        }

        return seatRepository.saveAll(seats);
    }

    /**
     * Returns all seats for a flight instance.
     */
    public List<Seat> findByFlightInstanceId(Long flightInstanceId) {
        if (!flightInstanceRepository.existsById(flightInstanceId)) {
            throw new ResourceNotFoundException("FlightInstance not found: " + flightInstanceId);
        }
        return seatRepository.findByFlightInstanceId(flightInstanceId);
    }
}
