package com.flightbooking.unit;

import com.flightbooking.booking.dto.BookingRequest;
import com.flightbooking.booking.dto.BookingResponse;
import com.flightbooking.booking.dto.PassengerRequest;
import com.flightbooking.booking.entity.*;
import com.flightbooking.booking.repository.BookingRepository;
import com.flightbooking.booking.repository.PassengerRepository;
import com.flightbooking.booking.repository.UserRepository;
import com.flightbooking.booking.service.BookingService;
import com.flightbooking.common.exception.ResourceNotFoundException;
import com.flightbooking.common.exception.SeatNotAvailableException;
import com.flightbooking.flight.entity.Airport;
import com.flightbooking.flight.entity.Flight;
import com.flightbooking.flight.entity.FlightInstance;
import com.flightbooking.flight.entity.FlightInstanceStatus;
import com.flightbooking.flight.repository.FlightInstanceRepository;
import com.flightbooking.payment.entity.Payment;
import com.flightbooking.payment.service.PaymentService;
import com.flightbooking.seat.entity.Seat;
import com.flightbooking.seat.service.SeatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private PassengerRepository passengerRepository;
    @Mock private UserRepository userRepository;
    @Mock private FlightInstanceRepository flightInstanceRepository;
    @Mock private SeatService seatService;
    @Mock private PaymentService paymentService;

    @InjectMocks private BookingService bookingService;

    private User user;
    private FlightInstance flightInstance;
    private Seat seat;
    private Passenger passenger;
    private BookingRequest request;

    @BeforeEach
    void setUp() {
        Airport delhi = new Airport();
        delhi.setId(1L);
        delhi.setCode("DEL");
        delhi.setCity("Delhi");

        Airport bangalore = new Airport();
        bangalore.setId(2L);
        bangalore.setCode("BLR");
        bangalore.setCity("Bangalore");

        Flight flight = new Flight();
        flight.setId(1L);
        flight.setFlightNumber("AI202");
        flight.setSourceAirport(delhi);
        flight.setDestinationAirport(bangalore);
        flight.setDefaultDepartureTime(LocalTime.of(22, 0));
        flight.setDefaultArrivalTime(LocalTime.of(0, 30));

        flightInstance = new FlightInstance();
        flightInstance.setId(1L);
        flightInstance.setFlight(flight);
        flightInstance.setTravelDate(LocalDate.now().plusDays(7));
        flightInstance.setDepartureTime(LocalTime.of(22, 0));
        flightInstance.setArrivalTime(LocalTime.of(0, 30));
        flightInstance.setFare(BigDecimal.valueOf(5500));
        flightInstance.setStatus(FlightInstanceStatus.SCHEDULED);
        flightInstance.setAvailableSeats(5);

        seat = new Seat(flightInstance, "1A");
        ReflectionTestUtils.setField(seat, "id", 1L);
        seat.hold(LocalDateTime.now(), Duration.ofMinutes(10));

        user = new User();
        user.setId(1L);
        user.setName("Vishal");
        user.setEmail("vishal@example.com");

        passenger = new Passenger("Vishal", 30, "Male");
        ReflectionTestUtils.setField(passenger, "id", 1L);

        request = new BookingRequest(1L, 1L, 1L,
                new PassengerRequest("Vishal", 30, "Male"));
    }

    @Test
    void initiateBooking_success_whenAllStepsSucceed() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(flightInstanceRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(flightInstance));
        when(seatService.holdSeat(1L)).thenReturn(seat);
        when(passengerRepository.save(any())).thenReturn(passenger);
        when(bookingRepository.save(any())).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            ReflectionTestUtils.setField(b, "id", 100L);
            return b;
        });

        Payment payment = new Payment(100L, BigDecimal.valueOf(5500), "idem-key-001");
        ReflectionTestUtils.setField(payment, "id", 1L);
        payment.markSuccess("TXN_ABC");
        when(paymentService.processPayment(anyLong(), any(), any())).thenReturn(payment);

        BookingResponse response = bookingService.initiateBooking(request, "idem-key-001");

        assertThat(response.bookingStatus()).isEqualTo("CONFIRMED");
        assertThat(response.payment().status()).isEqualTo("SUCCESS");
        assertThat(response.seatNumber()).isEqualTo("1A");
        assertThat(response.fare()).isEqualByComparingTo(BigDecimal.valueOf(5500));

        verify(seatService).holdSeat(1L);
        verify(seatService).confirmSeat(1L);
        verify(seatService, never()).releaseSeat(any());
    }

    @Test
    void initiateBooking_bookingFailed_whenPaymentFails() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(flightInstanceRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(flightInstance));
        when(seatService.holdSeat(1L)).thenReturn(seat);
        when(passengerRepository.save(any())).thenReturn(passenger);
        when(bookingRepository.save(any())).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            ReflectionTestUtils.setField(b, "id", 100L);
            return b;
        });

        Payment failedPayment = new Payment(100L, BigDecimal.valueOf(5500), "idem-key-002");
        ReflectionTestUtils.setField(failedPayment, "id", 2L);
        failedPayment.markFailed();
        when(paymentService.processPayment(anyLong(), any(), any())).thenReturn(failedPayment);

        BookingResponse response = bookingService.initiateBooking(request, "idem-key-002");

        assertThat(response.bookingStatus()).isEqualTo("FAILED");
        assertThat(response.payment().status()).isEqualTo("FAILED");

        // On payment failure: seat must be released, not confirmed
        verify(seatService).releaseSeat(1L);
        verify(seatService, never()).confirmSeat(any());
    }

    @Test
    void initiateBooking_throwsSeatNotAvailableException_whenSeatAlreadyBooked() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(flightInstanceRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(flightInstance));
        when(seatService.holdSeat(1L)).thenThrow(new SeatNotAvailableException("Seat 1A is not available"));

        assertThatThrownBy(() -> bookingService.initiateBooking(request, null))
                .isInstanceOf(SeatNotAvailableException.class)
                .hasMessageContaining("1A");

        // No passenger, no booking should be created
        verify(passengerRepository, never()).save(any());
        verify(bookingRepository, never()).save(any());
        verify(paymentService, never()).processPayment(any(), any(), any());
    }

    @Test
    void initiateBooking_throwsResourceNotFoundException_whenUserNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        BookingRequest badRequest = new BookingRequest(99L, 1L, 1L,
                new PassengerRequest("Someone", 25, "Male"));

        assertThatThrownBy(() -> bookingService.initiateBooking(badRequest, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");

        verify(seatService, never()).holdSeat(any());
    }

    @Test
    void initiateBooking_throwsResourceNotFoundException_whenFlightInstanceNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(flightInstanceRepository.findByIdWithDetails(99L)).thenReturn(Optional.empty());

        BookingRequest badRequest = new BookingRequest(1L, 99L, 1L,
                new PassengerRequest("Vishal", 30, "Male"));

        assertThatThrownBy(() -> bookingService.initiateBooking(badRequest, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");

        verify(seatService, never()).holdSeat(any());
    }

    @Test
    void initiateBooking_throwsSeatNotAvailableException_whenSeatBelongsToDifferentFlightInstance() {
        FlightInstance otherInstance = new FlightInstance();
        otherInstance.setId(99L); // different from request's flightInstanceId=1

        Seat seatFromOtherInstance = new Seat(otherInstance, "2A");
        ReflectionTestUtils.setField(seatFromOtherInstance, "id", 1L);
        seatFromOtherInstance.hold(LocalDateTime.now(), Duration.ofMinutes(10));

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(flightInstanceRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(flightInstance));
        when(seatService.holdSeat(1L)).thenReturn(seatFromOtherInstance);

        assertThatThrownBy(() -> bookingService.initiateBooking(request, null))
                .isInstanceOf(SeatNotAvailableException.class)
                .hasMessageContaining("does not belong to the requested flight instance");

        verify(passengerRepository, never()).save(any());
        verify(bookingRepository, never()).save(any());
        verify(paymentService, never()).processPayment(any(), any(), any());
    }

    @Test
    void initiateBooking_usesProvidedIdempotencyKey_forPayment() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(flightInstanceRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(flightInstance));
        when(seatService.holdSeat(1L)).thenReturn(seat);
        when(passengerRepository.save(any())).thenReturn(passenger);
        when(bookingRepository.save(any())).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            ReflectionTestUtils.setField(b, "id", 100L);
            return b;
        });

        Payment payment = new Payment(100L, BigDecimal.valueOf(5500), "my-custom-key");
        ReflectionTestUtils.setField(payment, "id", 1L);
        payment.markSuccess("TXN_X");
        when(paymentService.processPayment(anyLong(), any(), eq("my-custom-key"))).thenReturn(payment);

        bookingService.initiateBooking(request, "my-custom-key");

        verify(paymentService).processPayment(anyLong(), any(), eq("my-custom-key"));
    }
}
