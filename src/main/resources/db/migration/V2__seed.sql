-- ============================================================
-- Flight Booking System — Seed Data
-- ============================================================

-- Airports
INSERT INTO airports (code, city) VALUES
    ('DEL', 'Delhi'),
    ('BLR', 'Bangalore'),
    ('BOM', 'Mumbai'),
    ('MAA', 'Chennai');

-- Flights
-- AI202: Delhi → Bangalore
INSERT INTO flights (flight_number, source_airport_id, destination_airport_id, default_departure_time, default_arrival_time, default_fare, total_seats)
VALUES ('AI202', 1, 2, '22:00:00', '00:30:00', 5500.00, 5);

-- AI303: Delhi → Mumbai
INSERT INTO flights (flight_number, source_airport_id, destination_airport_id, default_departure_time, default_arrival_time, default_fare, total_seats)
VALUES ('AI303', 1, 3, '08:00:00', '10:15:00', 4200.00, 5);

-- AI404: Mumbai → Chennai
INSERT INTO flights (flight_number, source_airport_id, destination_airport_id, default_departure_time, default_arrival_time, default_fare, total_seats)
VALUES ('AI404', 3, 4, '13:30:00', '15:00:00', 3800.00, 5);

-- FlightInstances (snapshots for upcoming dates)
-- AI202 instances
INSERT INTO flight_instances (flight_id, travel_date, departure_time, arrival_time, fare, status, available_seats)
VALUES (1, CURRENT_DATE + 7,  '22:00:00', '00:30:00', 5500.00, 'SCHEDULED', 5);

INSERT INTO flight_instances (flight_id, travel_date, departure_time, arrival_time, fare, status, available_seats)
VALUES (1, CURRENT_DATE + 14, '22:00:00', '00:30:00', 5500.00, 'SCHEDULED', 5);

-- AI303 instances
INSERT INTO flight_instances (flight_id, travel_date, departure_time, arrival_time, fare, status, available_seats)
VALUES (2, CURRENT_DATE + 7,  '08:00:00', '10:15:00', 4200.00, 'SCHEDULED', 5);

INSERT INTO flight_instances (flight_id, travel_date, departure_time, arrival_time, fare, status, available_seats)
VALUES (2, CURRENT_DATE + 14, '08:00:00', '10:15:00', 4200.00, 'SCHEDULED', 5);

-- AI404 instance
INSERT INTO flight_instances (flight_id, travel_date, departure_time, arrival_time, fare, status, available_seats)
VALUES (3, CURRENT_DATE + 7,  '13:30:00', '15:00:00', 3800.00, 'SCHEDULED', 5);

-- Seats for flight_instance 1 (AI202, +7 days)
INSERT INTO seats (flight_instance_id, seat_number, status) VALUES
    (1, '1A', 'AVAILABLE'),
    (1, '1B', 'AVAILABLE'),
    (1, '1C', 'AVAILABLE'),
    (1, '2A', 'AVAILABLE'),
    (1, '2B', 'AVAILABLE');

-- Seats for flight_instance 2 (AI202, +14 days)
INSERT INTO seats (flight_instance_id, seat_number, status) VALUES
    (2, '1A', 'AVAILABLE'),
    (2, '1B', 'AVAILABLE'),
    (2, '1C', 'AVAILABLE'),
    (2, '2A', 'AVAILABLE'),
    (2, '2B', 'AVAILABLE');

-- Seats for flight_instance 3 (AI303, +7 days)
INSERT INTO seats (flight_instance_id, seat_number, status) VALUES
    (3, '1A', 'AVAILABLE'),
    (3, '1B', 'AVAILABLE'),
    (3, '1C', 'AVAILABLE'),
    (3, '2A', 'AVAILABLE'),
    (3, '2B', 'AVAILABLE');

-- Seats for flight_instance 4 (AI303, +14 days)
INSERT INTO seats (flight_instance_id, seat_number, status) VALUES
    (4, '1A', 'AVAILABLE'),
    (4, '1B', 'AVAILABLE'),
    (4, '1C', 'AVAILABLE'),
    (4, '2A', 'AVAILABLE'),
    (4, '2B', 'AVAILABLE');

-- Seats for flight_instance 5 (AI404, +7 days)
INSERT INTO seats (flight_instance_id, seat_number, status) VALUES
    (5, '1A', 'AVAILABLE'),
    (5, '1B', 'AVAILABLE'),
    (5, '1C', 'AVAILABLE'),
    (5, '2A', 'AVAILABLE'),
    (5, '2B', 'AVAILABLE');

-- Users
INSERT INTO users (name, email) VALUES
    ('Vishal Sharma',  'vishal@example.com'),
    ('Abhirub Acharya',  'abhirub@example.com'),
    ('Priya Patel',    'priya@example.com');
