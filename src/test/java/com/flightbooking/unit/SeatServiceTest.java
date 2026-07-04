package com.flightbooking.unit;

import com.flightbooking.common.exception.ResourceNotFoundException;
import com.flightbooking.common.exception.SeatNotAvailableException;
import com.flightbooking.flight.entity.FlightInstance;
import com.flightbooking.flight.repository.FlightInstanceRepository;
import com.flightbooking.seat.entity.Seat;
import com.flightbooking.seat.entity.SeatStatus;
import com.flightbooking.seat.repository.SeatRepository;
import com.flightbooking.seat.service.SeatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeatServiceTest {

    @Mock private SeatRepository seatRepository;
    @Mock private FlightInstanceRepository flightInstanceRepository;

    @InjectMocks private SeatService seatService;

    private Seat availableSeat;
    private Seat heldSeat;
    private Seat bookedSeat;
    private FlightInstance flightInstance;

    @BeforeEach
    void setUp() {
        flightInstance = new FlightInstance();
        ReflectionTestUtils.setField(flightInstance, "id", 1L);
        flightInstance.setAvailableSeats(5);

        availableSeat = new Seat(flightInstance, "1A");
        ReflectionTestUtils.setField(availableSeat, "id", 1L);

        heldSeat = new Seat(flightInstance, "1B");
        ReflectionTestUtils.setField(heldSeat, "id", 2L);
        heldSeat.hold(LocalDateTime.now(), Duration.ofMinutes(5));

        bookedSeat = new Seat(flightInstance, "1C");
        ReflectionTestUtils.setField(bookedSeat, "id", 3L);
        bookedSeat.hold(LocalDateTime.now(), Duration.ofMinutes(5));
        bookedSeat.confirm();
    }

    @Test
    void holdSeat_success_whenSeatIsAvailable() {
        when(seatRepository.findByIdWithLock(1L)).thenReturn(Optional.of(availableSeat));
        when(flightInstanceRepository.decrementAvailableSeats(1L)).thenReturn(1);
        when(seatRepository.save(any(Seat.class))).thenAnswer(inv -> inv.getArgument(0));

        Seat result = seatService.holdSeat(1L);

        assertThat(result.getStatus()).isEqualTo(SeatStatus.HELD);
        assertThat(result.getHoldExpiry()).isNotNull();
        assertThat(result.getHoldExpiry()).isAfter(LocalDateTime.now());
        verify(flightInstanceRepository).decrementAvailableSeats(1L);
    }

    @Test
    void holdSeat_throwsSeatNotAvailableException_whenSeatIsHeld() {
        when(seatRepository.findByIdWithLock(2L)).thenReturn(Optional.of(heldSeat));

        assertThatThrownBy(() -> seatService.holdSeat(2L))
                .isInstanceOf(SeatNotAvailableException.class)
                .hasMessageContaining("1B");
    }

    @Test
    void holdSeat_throwsSeatNotAvailableException_whenSeatIsBooked() {
        when(seatRepository.findByIdWithLock(3L)).thenReturn(Optional.of(bookedSeat));

        assertThatThrownBy(() -> seatService.holdSeat(3L))
                .isInstanceOf(SeatNotAvailableException.class)
                .hasMessageContaining("1C");
    }

    @Test
    void holdSeat_success_whenHoldIsExpired() {
        Seat expiredHold = new Seat(flightInstance, "2A");
        ReflectionTestUtils.setField(expiredHold, "id", 4L);
        // Base the hold 20 minutes in the past with a 10-minute TTL, so it expired 10 minutes ago.
        expiredHold.hold(LocalDateTime.now().minusMinutes(20), Duration.ofMinutes(10));

        when(seatRepository.findByIdWithLock(4L)).thenReturn(Optional.of(expiredHold));
        when(seatRepository.save(any(Seat.class))).thenAnswer(inv -> inv.getArgument(0));

        Seat result = seatService.holdSeat(4L);

        assertThat(result.getStatus()).isEqualTo(SeatStatus.HELD);
        assertThat(result.getHoldExpiry()).isAfter(LocalDateTime.now());
        // Reclaiming an expired hold must NOT decrement availableSeats again —
        // the original hold already consumed this unit of inventory.
        verify(flightInstanceRepository, never()).decrementAvailableSeats(any());
    }

    @Test
    void holdSeat_throwsResourceNotFoundException_whenSeatNotFound() {
        when(seatRepository.findByIdWithLock(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> seatService.holdSeat(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void confirmSeat_success_setsStatusToBooked() {
        when(seatRepository.findById(2L)).thenReturn(Optional.of(heldSeat));
        when(seatRepository.save(any(Seat.class))).thenAnswer(inv -> inv.getArgument(0));

        Seat result = seatService.confirmSeat(2L);

        assertThat(result.getStatus()).isEqualTo(SeatStatus.BOOKED);
        assertThat(result.getHoldExpiry()).isNull();
        verify(flightInstanceRepository, never()).incrementAvailableSeats(any());
    }

    @Test
    void releaseSeat_success_restoresSeatToAvailable() {
        when(seatRepository.findById(2L)).thenReturn(Optional.of(heldSeat));
        when(seatRepository.save(any(Seat.class))).thenAnswer(inv -> inv.getArgument(0));

        Seat result = seatService.releaseSeat(2L);

        assertThat(result.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(result.getHoldExpiry()).isNull();
        verify(flightInstanceRepository).incrementAvailableSeats(1L);
    }

    @Test
    void findByFlightInstanceId_returnsSeats_whenFlightInstanceExists() {
        when(flightInstanceRepository.existsById(1L)).thenReturn(true);
        when(seatRepository.findByFlightInstanceId(1L)).thenReturn(List.of(availableSeat, heldSeat, bookedSeat));

        List<Seat> result = seatService.findByFlightInstanceId(1L);

        assertThat(result).hasSize(3);
    }

    @Test
    void findByFlightInstanceId_throwsResourceNotFoundException_whenFlightInstanceMissing() {
        when(flightInstanceRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> seatService.findByFlightInstanceId(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");

        verify(seatRepository, never()).findByFlightInstanceId(any());
    }
}
