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

import java.time.LocalDateTime;
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
        flightInstance.setId(1L);
        flightInstance.setAvailableSeats(5);

        availableSeat = new Seat();
        availableSeat.setId(1L);
        availableSeat.setSeatNumber("1A");
        availableSeat.setStatus(SeatStatus.AVAILABLE);
        availableSeat.setFlightInstance(flightInstance);

        heldSeat = new Seat();
        heldSeat.setId(2L);
        heldSeat.setSeatNumber("1B");
        heldSeat.setStatus(SeatStatus.HELD);
        heldSeat.setHoldExpiry(LocalDateTime.now().plusMinutes(5));
        heldSeat.setFlightInstance(flightInstance);

        bookedSeat = new Seat();
        bookedSeat.setId(3L);
        bookedSeat.setSeatNumber("1C");
        bookedSeat.setStatus(SeatStatus.BOOKED);
        bookedSeat.setFlightInstance(flightInstance);
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
        Seat expiredHold = new Seat();
        expiredHold.setId(4L);
        expiredHold.setSeatNumber("2A");
        expiredHold.setStatus(SeatStatus.HELD);
        expiredHold.setHoldExpiry(LocalDateTime.now().minusMinutes(1));
        expiredHold.setFlightInstance(flightInstance);

        when(seatRepository.findByIdWithLock(4L)).thenReturn(Optional.of(expiredHold));
        when(flightInstanceRepository.decrementAvailableSeats(1L)).thenReturn(1);
        when(seatRepository.save(any(Seat.class))).thenAnswer(inv -> inv.getArgument(0));

        Seat result = seatService.holdSeat(4L);

        assertThat(result.getStatus()).isEqualTo(SeatStatus.HELD);
        assertThat(result.getHoldExpiry()).isAfter(LocalDateTime.now());
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
        heldSeat.setStatus(SeatStatus.HELD);
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
}
