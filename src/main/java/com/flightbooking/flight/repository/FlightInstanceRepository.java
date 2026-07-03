package com.flightbooking.flight.repository;

import com.flightbooking.flight.entity.FlightInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FlightInstanceRepository extends JpaRepository<FlightInstance, Long> {

    @Query("""
        SELECT fi FROM FlightInstance fi
        JOIN FETCH fi.flight f
        JOIN FETCH f.sourceAirport sa
        JOIN FETCH f.destinationAirport da
        WHERE sa.code = :sourceCode
          AND da.code = :destinationCode
          AND fi.travelDate = :travelDate
          AND fi.status = com.flightbooking.flight.entity.FlightInstanceStatus.SCHEDULED
        """)
    List<FlightInstance> findScheduledInstances(
            @Param("sourceCode") String sourceCode,
            @Param("destinationCode") String destinationCode,
            @Param("travelDate") LocalDate travelDate
    );

    Optional<FlightInstance> findByFlightIdAndTravelDate(Long flightId, LocalDate travelDate);

    @Query("""
        SELECT fi FROM FlightInstance fi
        JOIN FETCH fi.flight f
        JOIN FETCH f.sourceAirport
        JOIN FETCH f.destinationAirport
        WHERE fi.id = :id
        """)
    Optional<FlightInstance> findByIdWithDetails(@Param("id") Long id);

    // Atomic decrement to prevent race conditions when multiple seats are held concurrently
    @Modifying
    @Query("UPDATE FlightInstance fi SET fi.availableSeats = fi.availableSeats - 1 WHERE fi.id = :id AND fi.availableSeats > 0")
    int decrementAvailableSeats(@Param("id") Long id);

    @Modifying
    @Query("UPDATE FlightInstance fi SET fi.availableSeats = fi.availableSeats + 1 WHERE fi.id = :id")
    void incrementAvailableSeats(@Param("id") Long id);
}
