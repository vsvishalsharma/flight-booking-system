package com.flightbooking.flight.repository;

import com.flightbooking.flight.entity.Airport;
import com.flightbooking.flight.entity.Flight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FlightRepository extends JpaRepository<Flight, Long> {

    @Query("""
        SELECT f FROM Flight f
        JOIN FETCH f.sourceAirport sa
        JOIN FETCH f.destinationAirport da
        WHERE sa.id = :sourceId AND da.id = :destinationId
        """)
    List<Flight> findBySourceAndDestinationWithDetails(
            @Param("sourceId") Long sourceId,
            @Param("destinationId") Long destinationId
    );
}
