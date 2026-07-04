package com.flightbooking.booking.dto;

import com.flightbooking.booking.entity.Booking;
import com.flightbooking.booking.entity.Passenger;
import com.flightbooking.flight.entity.Flight;
import com.flightbooking.flight.entity.FlightInstance;
import com.flightbooking.payment.entity.Payment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record BookingResponse(
        Long bookingId,
        String bookingStatus,
        Long userId,
        PassengerInfo passenger,
        FlightInfo flight,
        String seatNumber,
        BigDecimal fare,
        PaymentInfo payment,
        LocalDateTime createdAt
) {

    public static BookingResponse from(Booking booking, String seatNumber, Payment payment) {
        return new BookingResponse(
                booking.getId(),
                booking.getStatus().name(),
                booking.getUser().getId(),
                PassengerInfo.from(booking.getPassenger()),
                FlightInfo.from(booking.getFlightInstance()),
                seatNumber,
                booking.getFare(),
                PaymentInfo.from(payment),
                booking.getCreatedAt()
        );
    }

    public record PassengerInfo(Long id, String name, Integer age, String gender) {

        public static PassengerInfo from(Passenger passenger) {
            return new PassengerInfo(passenger.getId(), passenger.getName(), passenger.getAge(), passenger.getGender());
        }
    }

    public record FlightInfo(
            Long flightInstanceId,
            String flightNumber,
            String sourceCode,
            String sourceCity,
            String destinationCode,
            String destinationCity,
            LocalDate travelDate,
            LocalTime departureTime,
            LocalTime arrivalTime
    ) {

        public static FlightInfo from(FlightInstance flightInstance) {
            Flight flight = flightInstance.getFlight();
            return new FlightInfo(
                    flightInstance.getId(),
                    flight.getFlightNumber(),
                    flight.getSourceAirport().getCode(),
                    flight.getSourceAirport().getCity(),
                    flight.getDestinationAirport().getCode(),
                    flight.getDestinationAirport().getCity(),
                    flightInstance.getTravelDate(),
                    flightInstance.getDepartureTime(),
                    flightInstance.getArrivalTime()
            );
        }
    }

    public record PaymentInfo(Long paymentId, String status, String gatewayTransactionId) {

        public static PaymentInfo from(Payment payment) {
            return new PaymentInfo(payment.getId(), payment.getStatus().name(), payment.getGatewayTransactionId());
        }
    }
}
