package com.flightbooking.flight.service;

import com.flightbooking.common.exception.ResourceNotFoundException;
import com.flightbooking.flight.dto.FlightInstanceResponse;
import com.flightbooking.flight.entity.*;
import com.flightbooking.flight.repository.AirportRepository;
import com.flightbooking.flight.repository.FlightInstanceRepository;
import com.flightbooking.flight.repository.FlightRepository;
import com.flightbooking.seat.entity.Seat;
import com.flightbooking.seat.entity.SeatStatus;
import com.flightbooking.seat.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchService {

    private final FlightInstanceRepository flightInstanceRepository;
    private final FlightRepository flightRepository;
    private final AirportRepository airportRepository;
    private final SeatRepository seatRepository;

    /**
     * Returns scheduled FlightInstances for the given route and date.
     *
     * FlightInstance creation is lazy: if no instance exists for a given date,
     * a new one is created as a snapshot of the current Flight definition.
     * Subsequent changes to the Flight do NOT affect this snapshot.
     */
    @Transactional
    public List<FlightInstanceResponse> search(String sourceCode, String destinationCode, LocalDate travelDate) {
        Airport source = airportRepository.findByCode(sourceCode)
                .orElseThrow(() -> new ResourceNotFoundException("Airport not found: " + sourceCode));
        Airport destination = airportRepository.findByCode(destinationCode)
                .orElseThrow(() -> new ResourceNotFoundException("Airport not found: " + destinationCode));

        List<FlightInstance> instances = flightInstanceRepository
                .findScheduledInstances(sourceCode, destinationCode, travelDate);

        if (instances.isEmpty()) {
            log.debug("No FlightInstances found for {} → {} on {}. Generating lazily.", sourceCode, destinationCode, travelDate);
            instances = generateInstances(source, destination, travelDate);
        }

        return instances.stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * For every Flight on the given route, creates a FlightInstance snapshot for the requested date
     * if one does not already exist.
     */
    private List<FlightInstance> generateInstances(Airport source, Airport destination, LocalDate travelDate) {
        List<Flight> flights = flightRepository.findBySourceAndDestinationWithDetails(
                source.getId(), destination.getId()
        );

        List<FlightInstance> result = new ArrayList<>();
        for (Flight flight : flights) {
            FlightInstance instance = flightInstanceRepository
                    .findByFlightIdAndTravelDate(flight.getId(), travelDate)
                    .orElseGet(() -> createInstanceSnapshot(flight, travelDate));
            result.add(instance);
        }
        return result;
    }

    private FlightInstance createInstanceSnapshot(Flight flight, LocalDate travelDate) {
        FlightInstance instance = new FlightInstance();
        instance.setFlight(flight);
        instance.setTravelDate(travelDate);
        instance.setDepartureTime(flight.getDefaultDepartureTime());
        instance.setArrivalTime(flight.getDefaultArrivalTime());
        instance.setFare(flight.getDefaultFare());
        instance.setStatus(FlightInstanceStatus.SCHEDULED);
        instance.setAvailableSeats(flight.getTotalSeats());

        FlightInstance saved = flightInstanceRepository.save(instance);
        createSeatsForInstance(saved, flight.getTotalSeats());

        log.info("Created FlightInstance snapshot: flight={} date={}", flight.getFlightNumber(), travelDate);
        return saved;
    }

    private void createSeatsForInstance(FlightInstance instance, int totalSeats) {
        List<Seat> seats = new ArrayList<>(totalSeats);
        String[] columns = {"A", "B", "C", "D", "E", "F"};
        int created = 0;
        int row = 1;

        while (created < totalSeats) {
            for (String col : columns) {
                if (created >= totalSeats) break;
                Seat seat = new Seat();
                seat.setFlightInstance(instance);
                seat.setSeatNumber(row + col);
                seat.setStatus(SeatStatus.AVAILABLE);
                seats.add(seat);
                created++;
            }
            row++;
        }

        seatRepository.saveAll(seats);
    }

    private FlightInstanceResponse toResponse(FlightInstance fi) {
        Flight flight = fi.getFlight();
        return new FlightInstanceResponse(
                fi.getId(),
                flight.getFlightNumber(),
                flight.getSourceAirport().getCode(),
                flight.getSourceAirport().getCity(),
                flight.getDestinationAirport().getCode(),
                flight.getDestinationAirport().getCity(),
                fi.getTravelDate(),
                fi.getDepartureTime(),
                fi.getArrivalTime(),
                fi.getFare(),
                fi.getStatus().name(),
                fi.getAvailableSeats()
        );
    }
}
