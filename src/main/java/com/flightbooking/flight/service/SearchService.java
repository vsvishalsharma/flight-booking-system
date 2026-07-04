package com.flightbooking.flight.service;

import com.flightbooking.common.exception.ResourceNotFoundException;
import com.flightbooking.flight.dto.FlightInstanceResponse;
import com.flightbooking.flight.entity.*;
import com.flightbooking.flight.repository.AirportRepository;
import com.flightbooking.flight.repository.FlightInstanceRepository;
import com.flightbooking.flight.repository.FlightRepository;
import com.flightbooking.seat.service.SeatService;
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
    private final SeatService seatService;

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
                .map(FlightInstanceResponse::from)
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
        FlightInstance saved = flightInstanceRepository.save(FlightInstance.snapshotOf(flight, travelDate));
        seatService.generateSeats(saved, flight.getTotalSeats());

        log.info("Created FlightInstance snapshot: flight={} date={}", flight.getFlightNumber(), travelDate);
        return saved;
    }
}
