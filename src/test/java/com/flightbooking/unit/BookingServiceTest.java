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
import com.flightbooking.payment.entity.PaymentStatus;
import com.flightbooking.payment.service.PaymentService;
import com.flightbooking.seat.entity.Seat;
import com.flightbooking.seat.entity.SeatStatus;
import com.flightbooking.seat.service.SeatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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

        seat = new Seat();
        seat.setId(1L);
        seat.setSeatNumber("1A");
        seat.setStatus(SeatStatus.HELD);
        seat.setFlightInstance(flightInstance);

        user = new User();
        user.setId(1L);
        user.setName("Vishal");
        user.setEmail("vishal@example.com");

        passenger = new Passenger();
        passenger.setId(1L);
        passenger.setName("Vishal");
        passenger.setAge(30);
        passenger.setGender("Male");

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
            b.setId(100L);
            return b;
        });

        Payment payment = new Payment();
        payment.setId(1L);
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setGatewayTransactionId("TXN_ABC");
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
            b.setId(100L);
            return b;
        });

        Payment failedPayment = new Payment();
        failedPayment.setId(2L);
        failedPayment.setStatus(PaymentStatus.FAILED);
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
    void initiateBooking_usesProvidedIdempotencyKey_forPayment() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(flightInstanceRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(flightInstance));
        when(seatService.holdSeat(1L)).thenReturn(seat);
        when(passengerRepository.save(any())).thenReturn(passenger);
        when(bookingRepository.save(any())).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId(100L);
            return b;
        });

        Payment payment = new Payment();
        payment.setId(1L);
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setGatewayTransactionId("TXN_X");
        when(paymentService.processPayment(anyLong(), any(), eq("my-custom-key"))).thenReturn(payment);

        bookingService.initiateBooking(request, "my-custom-key");

        verify(paymentService).processPayment(anyLong(), any(), eq("my-custom-key"));
    }
}
