package com.flightbooking.booking.repository;

import com.flightbooking.booking.entity.Passenger;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PassengerRepository extends JpaRepository<Passenger, Long> {
}
