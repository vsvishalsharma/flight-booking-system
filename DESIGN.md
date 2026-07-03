# Flight Booking System — Design Document

## Overview

A modular monolith flight booking system built with Java 21, Spring Boot 3, PostgreSQL, and Flyway.  
The system allows users to search for flights and book a seat for a passenger in a single synchronous flow.

---

## Architecture

```
┌─────────────────────────────────────────────────────┐
│                   REST API Layer                     │
│        FlightController   BookingController          │
└──────────────────┬──────────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────────┐
│                  Service Layer                       │
│   SearchService   BookingService (Aggregate Root)    │
│                 SeatService  PaymentService          │
└──────────────────┬──────────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────────┐
│               Repository Layer                       │
│   (Spring Data JPA — one repo per aggregate entity)  │
└──────────────────┬──────────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────────┐
│                 PostgreSQL                           │
└─────────────────────────────────────────────────────┘
```

### Module Layout

```
com.flightbooking
├── common          — shared DTOs, exceptions, global handler
├── config          — (reserved for future Spring beans)
├── flight          — Airport, Flight, FlightInstance, SearchService
├── seat            — Seat, SeatService
├── booking         — User, Passenger, Booking, BookingService (aggregate root)
└── payment         — Payment, PaymentService, PaymentGatewaySimulator
```

---

## Entity Model

### Airport
| Field | Type   | Notes           |
|-------|--------|-----------------|
| id    | BIGINT | PK              |
| code  | TEXT   | IATA code, unique |
| city  | TEXT   |                 |

### Flight
| Field                 | Type    | Notes                                         |
|-----------------------|---------|-----------------------------------------------|
| id                    | BIGINT  | PK                                            |
| flightNumber          | TEXT    | unique                                        |
| sourceAirportId       | BIGINT  | FK → Airport                                  |
| destinationAirportId  | BIGINT  | FK → Airport                                  |
| defaultDepartureTime  | TIME    | copied into FlightInstance snapshot           |
| defaultArrivalTime    | TIME    | copied into FlightInstance snapshot           |
| defaultFare           | DECIMAL | copied into FlightInstance snapshot (see below) |
| totalSeats            | INT     | used when generating seat records lazily      |

### FlightInstance  _(bookable occurrence)_
| Field          | Type    | Notes                                |
|----------------|---------|--------------------------------------|
| id             | BIGINT  | PK                                   |
| flightId       | BIGINT  | FK → Flight                          |
| travelDate     | DATE    |                                      |
| departureTime  | TIME    | snapshot — immutable after creation  |
| arrivalTime    | TIME    | snapshot — immutable after creation  |
| fare           | DECIMAL | snapshot — immutable after creation  |
| status         | ENUM    | SCHEDULED, CANCELLED                 |
| availableSeats | INT     | denormalized counter                 |

Unique constraint: `(flightId, travelDate)` — one instance per flight per day.

### Seat
| Field             | Type     | Notes                     |
|-------------------|----------|---------------------------|
| id                | BIGINT   | PK                        |
| flightInstanceId  | BIGINT   | FK → FlightInstance       |
| seatNumber        | TEXT     | e.g. "12A"                |
| status            | ENUM     | AVAILABLE, HELD, BOOKED   |
| holdExpiry        | TIMESTAMP | null unless currently HELD |

### User
| Field | Type | Notes         |
|-------|------|---------------|
| id    | BIGINT | PK          |
| name  | TEXT   |             |
| email | TEXT   | unique      |

### Passenger  _(created per booking)_
| Field  | Type    | Notes |
|--------|---------|-------|
| id     | BIGINT  | PK    |
| name   | TEXT    |       |
| age    | INT     |       |
| gender | TEXT    |       |

### Booking  _(aggregate root)_
| Field             | Type     | Notes                                       |
|-------------------|----------|---------------------------------------------|
| id                | BIGINT   | PK                                          |
| userId            | BIGINT   | FK → User                                   |
| passengerId       | BIGINT   | FK → Passenger                              |
| flightInstanceId  | BIGINT   | FK → FlightInstance                         |
| seatId            | BIGINT   | FK → Seat — Booking owns the seat reference |
| fare              | DECIMAL  | captured at booking time                    |
| status            | ENUM     | PENDING, CONFIRMED, FAILED, EXPIRED         |
| createdAt         | TIMESTAMP |                                            |

### Payment
| Field                 | Type     | Notes                       |
|-----------------------|----------|-----------------------------|
| id                    | BIGINT   | PK                          |
| bookingId             | BIGINT   | FK → Booking                |
| amount                | DECIMAL  |                             |
| status                | ENUM     | PENDING, SUCCESS, FAILED    |
| gatewayTransactionId  | TEXT     | null on failure             |
| idempotencyKey        | TEXT     | unique — idempotency guard  |
| createdAt             | TIMESTAMP |                            |

---

## Relationships

```
Airport (1) ──< (N) Flight
Flight (1) ──< (N) FlightInstance
FlightInstance (1) ──< (N) Seat
User (1) ──< (N) Booking
Passenger (1) ──< (1) Booking    (one passenger per booking)
Booking ──> Seat   (via seatId FK — Booking owns the reference)
Booking (1) ──< (1) Payment
```

---

## FlightInstance Creation Strategy (Key Design Decision)

FlightInstances are **created lazily on first search**.

### Trigger
When `GET /flights/search?source=DEL&destination=BLR&travelDate=2026-07-10` is called
and no `FlightInstance` exists for `AI202` on `2026-07-10`:

1. `SearchService` queries `FlightRepository` for all flights on the route.
2. For each flight with no existing instance on that date, a new `FlightInstance` is created by **copying** the current Flight fields:
   - `departureTime ← flight.defaultDepartureTime`
   - `arrivalTime ← flight.defaultArrivalTime`
   - `fare ← flight.defaultFare`
   - `availableSeats ← flight.totalSeats`
3. `Seat` records (`1A`, `1B`, …) are generated and persisted for the new instance.
4. The snapshot is **frozen at creation time**. Subsequent changes to the `Flight` row only affect future FlightInstances, not existing ones.

### Concurrency Note
If two concurrent requests try to create the same FlightInstance (same `flightId + travelDate`), the unique constraint `uq_flight_instance` will reject the second insert. In production this would be caught and retried. For this assignment scope, the probability is negligible and the constraint provides correctness.

---

## Search Flow

```
GET /flights/search?source=DEL&destination=BLR&travelDate=2026-07-10

SearchService
  → validate source airport exists
  → validate destination airport exists
  → query FlightInstances for route + date (JOIN FETCH flight + airports)
  → if empty: generate lazily (see above)
  → map to FlightInstanceResponse[]
  → return
```

---

## Booking Flow

```
POST /bookings/initiate
  Body: { userId, flightInstanceId, seatId, passenger }
  Header: X-Idempotency-Key (optional)

BookingService (single @Transactional boundary)
  1. Validate userId → User
  2. Validate flightInstanceId → FlightInstance (JOIN FETCH)
  3. SeatService.holdSeat(seatId)
       → SELECT FOR UPDATE on Seat
       → check status = AVAILABLE (or HELD with expired holdExpiry)
       → set status = HELD, holdExpiry = now + 10 min
       → atomic UPDATE flight_instances SET available_seats = available_seats - 1
  4. Create Passenger
  5. Create Booking (status = PENDING)
  6. PaymentService.processPayment(bookingId, fare, idempotencyKey)
       → check idempotency key → return existing if found
       → call PaymentGatewaySimulator.process()
       → persist Payment (SUCCESS or FAILED)
  7a. Payment SUCCESS
       → SeatService.confirmSeat(seatId) — status = BOOKED
       → Booking.status = CONFIRMED
  7b. Payment FAILED
       → SeatService.releaseSeat(seatId) — status = AVAILABLE, availableSeats++
       → Booking.status = FAILED
  8. Save Booking
  9. Return BookingResponse
```

---

## Failure Handling

### Seat Unavailable
- `SeatService.holdSeat()` throws `SeatNotAvailableException`
- Propagates out of `BookingService.initiateBooking()`
- Spring `@Transactional` rolls back the entire transaction
- No Passenger, Booking, or Payment record is created
- API returns `409 Conflict`

### Payment Failure
- `PaymentService.processPayment()` returns a `Payment` with `status = FAILED`
- `BookingService` catches the FAILED status (not an exception)
- Calls `SeatService.releaseSeat()` to restore inventory
- Sets `Booking.status = FAILED`
- Both FAILED Booking and FAILED Payment are persisted
- API returns `200 OK` with `bookingStatus = "FAILED"` (the request was processed; the business outcome was failure)

### Duplicate Payment Request
- `PaymentService` checks `paymentRepository.findByIdempotencyKey(key)`
- If found, returns the existing Payment immediately
- Gateway is not called again
- Booking outcome matches the original payment result

### Booking Rollback (Unexpected Exception)
- Any unexpected `RuntimeException` propagates out of the `@Transactional` boundary
- Spring rolls back the entire transaction
- No partial state is left in the database

---

## Assumptions

1. **Static pricing** — fare is fixed at FlightInstance creation and does not change.
2. **One booking = one passenger** — a booking cannot have multiple passengers.
3. **User must exist** — user creation is out of scope; users are seeded.
4. **No cancellations or refunds** — a CONFIRMED booking is final.
5. **No dynamic seat assignment** — the client chooses the specific seat; no auto-assign.
6. **Seat hold TTL** — holds expire after 10 minutes. In this synchronous implementation the hold and payment happen atomically, so the TTL guards only against abandoned partial flows.
7. **Payment gateway** — simulated synchronously; always returns SUCCESS in production code. Tests mock it to exercise failure paths.

---

## Design Deviations from Interview

See [CHANGELOG.md](CHANGELOG.md) for the full list of deviations with reasoning. The main ones are summarised below.

### Deviation 1 — Booking Owns Seat (not the reverse)

**Interview design**: Seat stores `bookingId` as a back-reference.  
**Implementation**: `Booking` stores `seatId`; `Seat` has no `bookingId` column.

**Why**: Seat is an inventory entity. Its lifecycle (AVAILABLE → HELD → BOOKED) is independent of any individual booking. Storing `bookingId` on Seat would create a coupling that makes releasing and re-booking a seat more complex. By letting Booking own the reference, the Seat table remains a clean inventory ledger.

**Benefit**: A seat can be released and re-booked without mutating `bookingId`. Failed bookings still exist in the Booking table without leaving a dangling `bookingId` reference on the Seat.

---

---

## Tradeoffs

| Decision | Tradeoff |
|----------|----------|
| Single `@Transactional` booking flow | Simple reasoning, no distributed state. Holds DB connection slightly longer due to synchronous payment call. Acceptable since payment is an in-process simulation. |
| Pessimistic write lock on Seat | Correct under concurrent booking; adds latency per-seat. Alternative (optimistic locking) would require retry logic. |
| Denormalized `availableSeats` on FlightInstance | Fast search queries. Must be kept consistent with Seat status changes via atomic SQL UPDATE. |
| Lazy FlightInstance creation | Clean snapshot model. Concurrent creation of same instance is guarded by unique constraint. |
| Payment always succeeds in production code | Simplifies the simulation. Failure paths are fully covered in unit tests via mocking. |
