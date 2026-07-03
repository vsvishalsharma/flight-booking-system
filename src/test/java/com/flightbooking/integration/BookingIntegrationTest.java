package com.flightbooking.integration;

import com.flightbooking.booking.entity.BookingStatus;
import com.flightbooking.booking.repository.BookingRepository;
import com.flightbooking.common.dto.ApiResponse;
import com.flightbooking.booking.dto.BookingRequest;
import com.flightbooking.booking.dto.BookingResponse;
import com.flightbooking.booking.dto.PassengerRequest;
import com.flightbooking.flight.dto.FlightInstanceResponse;
import com.flightbooking.payment.entity.PaymentStatus;
import com.flightbooking.payment.repository.PaymentRepository;
import com.flightbooking.seat.entity.SeatStatus;
import com.flightbooking.seat.repository.SeatRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class BookingIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("flightbooking_test")
            .withUsername("postgres")
            .withPassword("postgres");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private PaymentRepository paymentRepository;

    /**
     * End-to-end happy path:
     *   1. Search for a flight (uses seeded DEL → BLR data)
     *   2. Initiate booking with first available seat
     *   3. Verify Booking is CONFIRMED, Seat is BOOKED, Payment is SUCCESS
     */
    @Test
    void fullBookingFlow_searchThenBook_resultsInConfirmedBooking() {
        // Step 1: Search for flights (seed data has DEL → BLR at CURRENT_DATE + 7)
        String searchUrl = "/flights/search?source=DEL&destination=BLR&travelDate="
                + LocalDate.now().plusDays(7);

        ResponseEntity<ApiResponse<List<FlightInstanceResponse>>> searchResponse = restTemplate.exchange(
                searchUrl,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );

        assertThat(searchResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(searchResponse.getBody()).isNotNull();
        assertThat(searchResponse.getBody().success()).isTrue();

        List<FlightInstanceResponse> flights = searchResponse.getBody().data();
        assertThat(flights).isNotEmpty();

        FlightInstanceResponse selectedFlight = flights.get(0);
        assertThat(selectedFlight.sourceCode()).isEqualTo("DEL");
        assertThat(selectedFlight.destinationCode()).isEqualTo("BLR");
        assertThat(selectedFlight.availableSeats()).isGreaterThan(0);

        // Step 2: Get an available seat for this flight instance
        Long flightInstanceId = selectedFlight.flightInstanceId();
        Long seatId = seatRepository.findByFlightInstanceId(flightInstanceId).stream()
                .filter(s -> s.getStatus() == SeatStatus.AVAILABLE)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No available seat found in seed data"))
                .getId();

        // Step 3: Initiate booking (userId=1 from seed data)
        BookingRequest bookingRequest = new BookingRequest(
                1L,
                flightInstanceId,
                seatId,
                new PassengerRequest("Vishal Sharma", 30, "Male")
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Idempotency-Key", "integration-test-key-001");

        ResponseEntity<ApiResponse<BookingResponse>> bookingResponse = restTemplate.exchange(
                "/bookings/initiate",
                HttpMethod.POST,
                new HttpEntity<>(bookingRequest, headers),
                new ParameterizedTypeReference<>() {}
        );

        // Step 4: Verify response
        assertThat(bookingResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bookingResponse.getBody()).isNotNull();
        assertThat(bookingResponse.getBody().success()).isTrue();

        BookingResponse booking = bookingResponse.getBody().data();
        assertThat(booking.bookingStatus()).isEqualTo(BookingStatus.CONFIRMED.name());
        assertThat(booking.payment().status()).isEqualTo(PaymentStatus.SUCCESS.name());
        assertThat(booking.payment().gatewayTransactionId()).isNotNull();
        assertThat(booking.seatNumber()).isNotNull();
        assertThat(booking.flight().flightNumber()).isEqualTo("AI202");

        // Step 5: Verify database state directly
        Long bookingId = booking.bookingId();

        bookingRepository.findById(bookingId).ifPresentOrElse(
                b -> assertThat(b.getStatus()).isEqualTo(BookingStatus.CONFIRMED),
                () -> fail("Booking not found in database")
        );

        seatRepository.findById(seatId).ifPresentOrElse(
                s -> assertThat(s.getStatus()).isEqualTo(SeatStatus.BOOKED),
                () -> fail("Seat not found in database")
        );

        paymentRepository.findByBookingId(bookingId).ifPresentOrElse(
                p -> {
                    assertThat(p.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
                    assertThat(p.getIdempotencyKey()).isEqualTo("integration-test-key-001");
                },
                () -> fail("Payment not found in database")
        );
    }

    @Test
    void searchFlight_lazyCreatesFlightInstance_whenNoneExistForDate() {
        // Search for a date that has no seeded data — system should create a FlightInstance lazily
        LocalDate futureDate = LocalDate.now().plusDays(60);
        String searchUrl = "/flights/search?source=DEL&destination=BLR&travelDate=" + futureDate;

        ResponseEntity<ApiResponse<List<FlightInstanceResponse>>> response = restTemplate.exchange(
                searchUrl,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<FlightInstanceResponse> flights = response.getBody().data();
        assertThat(flights).isNotEmpty();
        assertThat(flights.get(0).travelDate()).isEqualTo(futureDate);
        assertThat(flights.get(0).availableSeats()).isGreaterThan(0);
    }

    @Test
    void initiateBooking_returns409_whenSeatAlreadyBooked() {
        // First, get a seat from seeded data
        LocalDate date = LocalDate.now().plusDays(14);
        String searchUrl = "/flights/search?source=DEL&destination=BLR&travelDate=" + date;

        ResponseEntity<ApiResponse<List<FlightInstanceResponse>>> searchResponse = restTemplate.exchange(
                searchUrl, HttpMethod.GET, null, new ParameterizedTypeReference<>() {});

        FlightInstanceResponse flight = searchResponse.getBody().data().get(0);
        Long seatId = seatRepository.findByFlightInstanceId(flight.flightInstanceId()).stream()
                .filter(s -> s.getStatus() == SeatStatus.AVAILABLE)
                .findFirst()
                .orElseThrow()
                .getId();

        BookingRequest firstRequest = new BookingRequest(1L, flight.flightInstanceId(), seatId,
                new PassengerRequest("First Passenger", 25, "Male"));
        BookingRequest secondRequest = new BookingRequest(2L, flight.flightInstanceId(), seatId,
                new PassengerRequest("Second Passenger", 26, "Female"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // First booking should succeed
        headers.set("X-Idempotency-Key", "seat-conflict-test-first");
        restTemplate.exchange("/bookings/initiate", HttpMethod.POST,
                new HttpEntity<>(firstRequest, headers), new ParameterizedTypeReference<ApiResponse<BookingResponse>>() {});

        // Second booking for same seat should fail with 409
        headers.set("X-Idempotency-Key", "seat-conflict-test-second");
        ResponseEntity<ApiResponse<Void>> conflictResponse = restTemplate.exchange(
                "/bookings/initiate", HttpMethod.POST,
                new HttpEntity<>(secondRequest, headers), new ParameterizedTypeReference<>() {});

        assertThat(conflictResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void initiateBooking_returns409_whenSeatBelongsToDifferentFlightInstance() {
        LocalDate date = LocalDate.now().plusDays(7);

        // Get a seat from the DEL→BOM flight instance (AI303)
        ResponseEntity<ApiResponse<List<FlightInstanceResponse>>> bomSearch = restTemplate.exchange(
                "/flights/search?source=DEL&destination=BOM&travelDate=" + date,
                HttpMethod.GET, null, new ParameterizedTypeReference<>() {});
        Long bomInstanceId = bomSearch.getBody().data().get(0).flightInstanceId();
        Long seatFromBom = seatRepository.findByFlightInstanceId(bomInstanceId).stream()
                .filter(s -> s.getStatus() == SeatStatus.AVAILABLE)
                .findFirst()
                .orElseThrow()
                .getId();

        // Get the DEL→BLR flight instance (AI202) — a different flight instance
        ResponseEntity<ApiResponse<List<FlightInstanceResponse>>> blrSearch = restTemplate.exchange(
                "/flights/search?source=DEL&destination=BLR&travelDate=" + date,
                HttpMethod.GET, null, new ParameterizedTypeReference<>() {});
        Long blrInstanceId = blrSearch.getBody().data().get(0).flightInstanceId();

        // Attempt to book the BOM seat against the BLR flight instance
        BookingRequest crossRequest = new BookingRequest(
                1L, blrInstanceId, seatFromBom,
                new PassengerRequest("Cross Tester", 30, "Male")
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Idempotency-Key", "cross-instance-test-001");

        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                "/bookings/initiate", HttpMethod.POST,
                new HttpEntity<>(crossRequest, headers),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void searchFlight_returns404_whenAirportCodeInvalid() {
        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                "/flights/search?source=XXX&destination=BLR&travelDate=" + LocalDate.now().plusDays(7),
                HttpMethod.GET, null, new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
