-- ============================================================
-- Flight Booking System — Database Schema
-- ============================================================

CREATE TABLE airports (
    id      BIGSERIAL    PRIMARY KEY,
    code    VARCHAR(10)  NOT NULL UNIQUE,
    city    VARCHAR(100) NOT NULL
);

CREATE TABLE flights (
    id                      BIGSERIAL     PRIMARY KEY,
    flight_number           VARCHAR(20)   NOT NULL UNIQUE,
    source_airport_id       BIGINT        NOT NULL REFERENCES airports(id),
    destination_airport_id  BIGINT        NOT NULL REFERENCES airports(id),
    default_departure_time  TIME          NOT NULL,
    default_arrival_time    TIME          NOT NULL,
    -- defaultFare added to support lazy FlightInstance creation (snapshot of fare at creation time)
    default_fare            NUMERIC(10,2) NOT NULL,
    total_seats             INT           NOT NULL DEFAULT 60
);

-- FlightInstance is a snapshot of a Flight for a specific travel date.
-- Once created, it is immutable with respect to Flight changes.
-- Unique constraint ensures one instance per flight per date.
CREATE TABLE flight_instances (
    id               BIGSERIAL     PRIMARY KEY,
    flight_id        BIGINT        NOT NULL REFERENCES flights(id),
    travel_date      DATE          NOT NULL,
    departure_time   TIME          NOT NULL,
    arrival_time     TIME          NOT NULL,
    fare             NUMERIC(10,2) NOT NULL,
    status           VARCHAR(20)   NOT NULL DEFAULT 'SCHEDULED',
    available_seats  INT           NOT NULL,
    CONSTRAINT uq_flight_instance UNIQUE (flight_id, travel_date)
);

CREATE TABLE seats (
    id                 BIGSERIAL    PRIMARY KEY,
    flight_instance_id BIGINT       NOT NULL REFERENCES flight_instances(id),
    seat_number        VARCHAR(10)  NOT NULL,
    status             VARCHAR(20)  NOT NULL DEFAULT 'AVAILABLE',
    hold_expiry        TIMESTAMP,
    CONSTRAINT uq_seat UNIQUE (flight_instance_id, seat_number)
);

CREATE TABLE users (
    id    BIGSERIAL    PRIMARY KEY,
    name  VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE
);

-- Passenger is created per booking; it is NOT the same as User.
CREATE TABLE passengers (
    id      BIGSERIAL    PRIMARY KEY,
    name    VARCHAR(100) NOT NULL,
    age     INT          NOT NULL,
    gender  VARCHAR(20)  NOT NULL
);

-- Booking is the aggregate root.
-- seatId is stored directly in Booking (Booking owns the seat reference — Design Decision 1).
-- Seat does NOT store a bookingId back-reference.
CREATE TABLE bookings (
    id                 BIGSERIAL     PRIMARY KEY,
    user_id            BIGINT        NOT NULL REFERENCES users(id),
    passenger_id       BIGINT        NOT NULL REFERENCES passengers(id),
    flight_instance_id BIGINT        NOT NULL REFERENCES flight_instances(id),
    seat_id            BIGINT        NOT NULL REFERENCES seats(id),
    fare               NUMERIC(10,2) NOT NULL,
    status             VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    created_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- idempotency_key ensures that a duplicate payment request returns the existing result.
CREATE TABLE payments (
    id                      BIGSERIAL     PRIMARY KEY,
    booking_id              BIGINT        NOT NULL REFERENCES bookings(id),
    amount                  NUMERIC(10,2) NOT NULL,
    status                  VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    gateway_transaction_id  VARCHAR(100),
    idempotency_key         VARCHAR(255)  NOT NULL UNIQUE,
    created_at              TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for common query patterns
CREATE INDEX idx_flight_instances_search
    ON flight_instances(flight_id, travel_date, status);

CREATE INDEX idx_seats_flight_instance
    ON seats(flight_instance_id, status);

CREATE INDEX idx_bookings_user
    ON bookings(user_id);

CREATE INDEX idx_payments_booking
    ON payments(booking_id);
