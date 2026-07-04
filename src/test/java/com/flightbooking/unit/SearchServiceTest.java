package com.flightbooking.unit;

import com.flightbooking.common.exception.ResourceNotFoundException;
import com.flightbooking.flight.dto.FlightInstanceResponse;
import com.flightbooking.flight.entity.*;
import com.flightbooking.flight.repository.AirportRepository;
import com.flightbooking.flight.repository.FlightInstanceRepository;
import com.flightbooking.flight.repository.FlightRepository;
import com.flightbooking.flight.service.SearchService;
import com.flightbooking.seat.service.SeatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock private FlightInstanceRepository flightInstanceRepository;
    @Mock private FlightRepository flightRepository;
    @Mock private AirportRepository airportRepository;
    @Mock private SeatService seatService;

    @InjectMocks private SearchService searchService;

    private Airport delhi;
    private Airport bangalore;
    private Flight ai202;
    private FlightInstance fi;
    private final LocalDate travelDate = LocalDate.now().plusDays(7);

    @BeforeEach
    void setUp() {
        delhi = new Airport();
        delhi.setId(1L);
        delhi.setCode("DEL");
        delhi.setCity("Delhi");

        bangalore = new Airport();
        bangalore.setId(2L);
        bangalore.setCode("BLR");
        bangalore.setCity("Bangalore");

        ai202 = new Flight();
        ai202.setId(1L);
        ai202.setFlightNumber("AI202");
        ai202.setSourceAirport(delhi);
        ai202.setDestinationAirport(bangalore);
        ai202.setDefaultDepartureTime(LocalTime.of(22, 0));
        ai202.setDefaultArrivalTime(LocalTime.of(0, 30));
        ai202.setDefaultFare(BigDecimal.valueOf(5500));
        ai202.setTotalSeats(5);

        fi = new FlightInstance();
        fi.setId(1L);
        fi.setFlight(ai202);
        fi.setTravelDate(travelDate);
        fi.setDepartureTime(LocalTime.of(22, 0));
        fi.setArrivalTime(LocalTime.of(0, 30));
        fi.setFare(BigDecimal.valueOf(5500));
        fi.setStatus(FlightInstanceStatus.SCHEDULED);
        fi.setAvailableSeats(5);
    }

    @Test
    void search_returnsExistingInstances_whenInstancesExistForDate() {
        when(airportRepository.findByCode("DEL")).thenReturn(Optional.of(delhi));
        when(airportRepository.findByCode("BLR")).thenReturn(Optional.of(bangalore));
        when(flightInstanceRepository.findScheduledInstances("DEL", "BLR", travelDate))
                .thenReturn(List.of(fi));

        List<FlightInstanceResponse> results = searchService.search("DEL", "BLR", travelDate);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).flightInstanceId()).isEqualTo(1L);
        assertThat(results.get(0).flightNumber()).isEqualTo("AI202");
        assertThat(results.get(0).fare()).isEqualByComparingTo(BigDecimal.valueOf(5500));
        assertThat(results.get(0).availableSeats()).isEqualTo(5);

        verify(flightRepository, never()).findBySourceAndDestinationWithDetails(any(), any());
    }

    @Test
    void search_createsFlightInstanceLazily_whenNoInstanceExistsForDate() {
        LocalDate futureDate = LocalDate.now().plusDays(30);
        when(airportRepository.findByCode("DEL")).thenReturn(Optional.of(delhi));
        when(airportRepository.findByCode("BLR")).thenReturn(Optional.of(bangalore));
        when(flightInstanceRepository.findScheduledInstances("DEL", "BLR", futureDate))
                .thenReturn(Collections.emptyList());
        when(flightRepository.findBySourceAndDestinationWithDetails(1L, 2L))
                .thenReturn(List.of(ai202));
        when(flightInstanceRepository.findByFlightIdAndTravelDate(1L, futureDate))
                .thenReturn(Optional.empty());
        when(flightInstanceRepository.save(any(FlightInstance.class))).thenAnswer(inv -> {
            FlightInstance saved = inv.getArgument(0);
            saved.setId(99L);
            return saved;
        });

        List<FlightInstanceResponse> results = searchService.search("DEL", "BLR", futureDate);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).departureTime()).isEqualTo(LocalTime.of(22, 0));
        assertThat(results.get(0).fare()).isEqualByComparingTo(BigDecimal.valueOf(5500));
        assertThat(results.get(0).availableSeats()).isEqualTo(5);

        verify(flightInstanceRepository).save(any(FlightInstance.class));
        verify(seatService).generateSeats(any(FlightInstance.class), eq(5));
    }

    @Test
    void search_existingFlightInstance_departureTimeFrozenAtSnapshotCreation() {
        // Prove that SearchService reads departure time from the FlightInstance's own fields,
        // not from the parent Flight. Mutate the Flight to 06:00 after the snapshot was
        // already created at 22:00 — the response must still return 22:00.
        ai202.setDefaultDepartureTime(LocalTime.of(6, 0));   // Flight "updated" to 06:00
        // fi.departureTime is still 22:00 — it was frozen at snapshot creation time

        when(airportRepository.findByCode("DEL")).thenReturn(Optional.of(delhi));
        when(airportRepository.findByCode("BLR")).thenReturn(Optional.of(bangalore));
        when(flightInstanceRepository.findScheduledInstances("DEL", "BLR", travelDate))
                .thenReturn(List.of(fi));

        List<FlightInstanceResponse> results = searchService.search("DEL", "BLR", travelDate);

        // Response must reflect the snapshot (22:00), not the updated Flight schedule (06:00)
        assertThat(results.get(0).departureTime()).isEqualTo(LocalTime.of(22, 0));
        assertThat(results.get(0).departureTime()).isNotEqualTo(ai202.getDefaultDepartureTime());
    }

    @Test
    void search_throwsResourceNotFoundException_whenSourceAirportNotFound() {
        when(airportRepository.findByCode("XYZ")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> searchService.search("XYZ", "BLR", travelDate))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("XYZ");
    }

    @Test
    void search_throwsResourceNotFoundException_whenDestinationAirportNotFound() {
        when(airportRepository.findByCode("DEL")).thenReturn(Optional.of(delhi));
        when(airportRepository.findByCode("ABC")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> searchService.search("DEL", "ABC", travelDate))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("ABC");
    }

    @Test
    void search_returnsEmptyList_whenNoFlightsExistOnRoute() {
        when(airportRepository.findByCode("DEL")).thenReturn(Optional.of(delhi));
        when(airportRepository.findByCode("BLR")).thenReturn(Optional.of(bangalore));
        when(flightInstanceRepository.findScheduledInstances("DEL", "BLR", travelDate))
                .thenReturn(Collections.emptyList());
        when(flightRepository.findBySourceAndDestinationWithDetails(1L, 2L))
                .thenReturn(Collections.emptyList());

        List<FlightInstanceResponse> results = searchService.search("DEL", "BLR", travelDate);

        assertThat(results).isEmpty();
    }
}
