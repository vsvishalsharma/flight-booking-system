package com.flightbooking.booking.service;

import com.flightbooking.booking.dto.BookingRequest;
import com.flightbooking.booking.dto.BookingResponse;
import com.flightbooking.booking.entity.Booking;
import com.flightbooking.booking.entity.BookingStatus;
import com.flightbooking.booking.entity.Passenger;
import com.flightbooking.booking.entity.User;
import com.flightbooking.booking.repository.BookingRepository;
import com.flightbooking.booking.repository.PassengerRepository;
import com.flightbooking.booking.repository.UserRepository;
import com.flightbooking.common.exception.ResourceNotFoundException;
import com.flightbooking.flight.entity.Flight;
import com.flightbooking.flight.entity.FlightInstance;
import com.flightbooking.flight.repository.FlightInstanceRepository;
import com.flightbooking.payment.entity.Payment;
import com.flightbooking.payment.entity.PaymentStatus;
import com.flightbooking.payment.service.PaymentService;
import com.flightbooking.seat.entity.Seat;
import com.flightbooking.seat.service.SeatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Booking is the Aggregate Root (Design Decision 2).
 *
 * BookingService is the sole orchestrator of the booking flow.
 * SeatService and PaymentService are pure domain services: they execute their
 * own responsibilities but never read or modify the Booking entity.
 *
 * Flow:
 *   1. Validate user, flightInstance, seat references
 *   2. SeatService.holdSeat()          — inventory reserved
 *   3. Create Passenger                 — travel details captured
 *   4. Create Booking (PENDING)         — reservation record opened
 *   5. PaymentService.processPayment()  — charge attempted
 *   6a. SUCCESS → confirmSeat(), Booking → CONFIRMED
 *   6b. FAILED  → releaseSeat(), Booking → FAILED
 *   7. Return BookingResponse
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private final BookingRepository bookingRepository;
    private final PassengerRepository passengerRepository;
    private final UserRepository userRepository;
    private final FlightInstanceRepository flightInstanceRepository;
    private final SeatService seatService;
    private final PaymentService paymentService;

    @Transactional
    public BookingResponse initiateBooking(BookingRequest request, String idempotencyKey) {
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + request.userId()));

        FlightInstance flightInstance = flightInstanceRepository.findByIdWithDetails(request.flightInstanceId())
                .orElseThrow(() -> new ResourceNotFoundException("FlightInstance not found: " + request.flightInstanceId()));

        // Step 2: Hold the seat (pessimistic lock prevents double-booking)
        Seat seat = seatService.holdSeat(request.seatId());

        // Step 3: Create passenger
        Passenger passenger = createPassenger(request);

        // Step 4: Create booking in PENDING state
        Booking booking = new Booking();
        booking.setUser(user);
        booking.setPassenger(passenger);
        booking.setFlightInstance(flightInstance);
        booking.setSeatId(request.seatId());
        booking.setFare(flightInstance.getFare());
        booking.setStatus(BookingStatus.PENDING);
        booking.setCreatedAt(LocalDateTime.now());
        booking = bookingRepository.save(booking);

        log.info("Booking {} created in PENDING state for user {}", booking.getId(), user.getId());

        // Step 5: Process payment
        String effectiveKey = resolveIdempotencyKey(idempotencyKey, booking.getId());
        Payment payment = paymentService.processPayment(booking.getId(), flightInstance.getFare(), effectiveKey);

        // Step 6: Finalize based on payment outcome
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            seatService.confirmSeat(seat.getId());
            booking.setStatus(BookingStatus.CONFIRMED);
            log.info("Booking {} CONFIRMED. Payment txn: {}", booking.getId(), payment.getGatewayTransactionId());
        } else {
            seatService.releaseSeat(seat.getId());
            booking.setStatus(BookingStatus.FAILED);
            log.warn("Booking {} FAILED. Payment status: {}", booking.getId(), payment.getStatus());
        }

        booking = bookingRepository.save(booking);
        return buildResponse(booking, seat, flightInstance, payment);
    }

    private Passenger createPassenger(BookingRequest request) {
        Passenger passenger = new Passenger();
        passenger.setName(request.passenger().name());
        passenger.setAge(request.passenger().age());
        passenger.setGender(request.passenger().gender());
        return passengerRepository.save(passenger);
    }

    private String resolveIdempotencyKey(String clientKey, Long bookingId) {
        // Use client-provided key for idempotency; fall back to a booking-scoped key
        return (clientKey != null && !clientKey.isBlank())
                ? clientKey
                : "booking-" + bookingId + "-" + UUID.randomUUID();
    }

    private BookingResponse buildResponse(Booking booking, Seat seat, FlightInstance fi, Payment payment) {
        Flight flight = fi.getFlight();

        return new BookingResponse(
                booking.getId(),
                booking.getStatus().name(),
                booking.getUser().getId(),
                new BookingResponse.PassengerInfo(
                        booking.getPassenger().getId(),
                        booking.getPassenger().getName(),
                        booking.getPassenger().getAge(),
                        booking.getPassenger().getGender()
                ),
                new BookingResponse.FlightInfo(
                        fi.getId(),
                        flight.getFlightNumber(),
                        flight.getSourceAirport().getCode(),
                        flight.getSourceAirport().getCity(),
                        flight.getDestinationAirport().getCode(),
                        flight.getDestinationAirport().getCity(),
                        fi.getTravelDate(),
                        fi.getDepartureTime(),
                        fi.getArrivalTime()
                ),
                seat.getSeatNumber(),
                booking.getFare(),
                new BookingResponse.PaymentInfo(
                        payment.getId(),
                        payment.getStatus().name(),
                        payment.getGatewayTransactionId()
                ),
                booking.getCreatedAt()
        );
    }
}
