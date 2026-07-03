# Flight Booking System

A modular monolith flight booking API built with Java 21, Spring Boot 3, PostgreSQL, and Flyway.

---

## Architecture

```
┌────────────────────────────────────┐
│           REST Controllers         │
│   /flights/search                  │
│   /bookings/initiate               │
├────────────────────────────────────┤
│           Service Layer            │
│   SearchService (read-only)        │
│   BookingService (aggregate root)  │
│   SeatService                      │
│   PaymentService                   │
├────────────────────────────────────┤
│       Spring Data JPA + Flyway     │
├────────────────────────────────────┤
│           PostgreSQL 16            │
└────────────────────────────────────┘
```

**Modules**: `flight` · `seat` · `booking` · `payment` · `common`

---

## Running with Docker (Recommended)

**Requires**: Docker and Docker Compose

```bash
docker compose up
```

This will:
1. Start a PostgreSQL 16 container
2. Build the application JAR (multi-stage Docker build)
3. Run Flyway migrations (schema + seed data)
4. Start the application on port **8082**

> **Ports**: The app is mapped to `8082` (not 8080) and the database to `5433` (not 5432) to avoid conflicts with common local services. Both can be changed in `docker-compose.yml` if needed.

To rebuild after code changes:
```bash
docker compose up --build
```

To stop:
```bash
docker compose down
```

---

## Running Locally (Development)

**Requires**: Java 21, Maven 3.9+, PostgreSQL 16 running locally

1. Create the database:
```sql
CREATE DATABASE flightbooking;
```

2. Update credentials if needed in `src/main/resources/application.yml`.

3. Build and run:
```bash
mvn clean package -DskipTests
java -jar target/flight-booking-1.0.0.jar
```

---

## Running Tests

```bash
mvn test
```

Tests use **Testcontainers** — Docker must be running. PostgreSQL is spun up automatically for integration tests.

---

## API Documentation

### 1. Search Flights

```
GET /flights/search
```

**Query Parameters**

| Parameter   | Type   | Required | Example      |
|-------------|--------|----------|--------------|
| source      | String | Yes      | `DEL`        |
| destination | String | Yes      | `BLR`        |
| travelDate  | Date   | Yes      | `2026-07-10` |

**Response**

```json
{
  "success": true,
  "message": "Success",
  "data": [
    {
      "flightInstanceId": 1,
      "flightNumber": "AI202",
      "sourceCode": "DEL",
      "sourceCity": "Delhi",
      "destinationCode": "BLR",
      "destinationCity": "Bangalore",
      "travelDate": "2026-07-10",
      "departureTime": "22:00:00",
      "arrivalTime": "00:30:00",
      "fare": 5500.00,
      "status": "SCHEDULED",
      "availableSeats": 5
    }
  ]
}
```

**Notes**
- If no FlightInstance exists for the requested date, one is created **lazily** as a snapshot of the current Flight schedule.
- Existing FlightInstances are returned unchanged — they reflect the schedule at the time they were created.

---

### 2. Initiate Booking

```
POST /bookings/initiate
```

**Headers**

| Header              | Required | Description                                |
|---------------------|----------|--------------------------------------------|
| Content-Type        | Yes      | `application/json`                         |
| X-Idempotency-Key   | No       | Unique key to make the request idempotent  |

**Request Body**

```json
{
  "userId": 1,
  "flightInstanceId": 1,
  "seatId": 3,
  "passenger": {
    "name": "Vishal Sharma",
    "age": 30,
    "gender": "Male"
  }
}
```

**Response (CONFIRMED)**

```json
{
  "success": true,
  "message": "Success",
  "data": {
    "bookingId": 42,
    "bookingStatus": "CONFIRMED",
    "userId": 1,
    "passenger": {
      "id": 7,
      "name": "Vishal Sharma",
      "age": 30,
      "gender": "Male"
    },
    "flight": {
      "flightInstanceId": 1,
      "flightNumber": "AI202",
      "sourceCode": "DEL",
      "sourceCity": "Delhi",
      "destinationCode": "BLR",
      "destinationCity": "Bangalore",
      "travelDate": "2026-07-10",
      "departureTime": "22:00:00",
      "arrivalTime": "00:30:00"
    },
    "seatNumber": "1A",
    "fare": 5500.00,
    "payment": {
      "paymentId": 12,
      "status": "SUCCESS",
      "gatewayTransactionId": "TXN4F2A8B1C9D3E"
    },
    "createdAt": "2026-07-03T14:30:00"
  }
}
```

**Booking Status Values**

| Status    | Meaning                              |
|-----------|--------------------------------------|
| CONFIRMED | Seat held, payment succeeded         |
| FAILED    | Seat was released, payment failed    |
| PENDING   | Transient state during processing    |

**Error Responses**

| HTTP Status | Scenario                           |
|-------------|-------------------------------------|
| 400         | Validation failure (missing fields) |
| 404         | User, FlightInstance, or Seat not found |
| 409         | Seat already booked or held         |
| 500         | Unexpected server error             |

---

## Sample Requests (curl)

**Search flights**
```bash
curl "http://localhost:8082/flights/search?source=DEL&destination=BLR&travelDate=$(date -d '+7 days' +%Y-%m-%d)"
```

**Get seat IDs for a flight instance** (replace `1` with the `flightInstanceId` from search):
```bash
# Connect to PostgreSQL and run:
SELECT id, seat_number, status FROM seats WHERE flight_instance_id = 1 AND status = 'AVAILABLE';
```

**Initiate booking**
```bash
curl -X POST http://localhost:8082/bookings/initiate \
  -H "Content-Type: application/json" \
  -H "X-Idempotency-Key: my-unique-request-001" \
  -d '{
    "userId": 1,
    "flightInstanceId": 1,
    "seatId": 3,
    "passenger": {
      "name": "Vishal Sharma",
      "age": 30,
      "gender": "Male"
    }
  }'
```

---

## Seed Data

Flyway runs `V2__seed.sql` on startup. The following data is pre-loaded:

**Airports**: DEL (Delhi), BLR (Bangalore), BOM (Mumbai), MAA (Chennai)

**Flights**:
- `AI202` — DEL → BLR, 22:00 → 00:30, ₹5500
- `AI303` — DEL → BOM, 08:00 → 10:15, ₹4200
- `AI404` — BOM → MAA, 13:30 → 15:00, ₹3800

**FlightInstances**: Pre-seeded for `CURRENT_DATE + 7` and `CURRENT_DATE + 14` for AI202 and AI303, and `CURRENT_DATE + 7` for AI404.

**Seats**: 5 seats (1A–2B) per pre-seeded FlightInstance.

**Users**:
- ID 1: Vishal Sharma (vishal@example.com)
- ID 2: Abhirub Acharya (abhirub@example.com)
- ID 3: Priya Patel (priya@example.com)

---

## Assumptions

1. One booking contains exactly one passenger.
2. User and Passenger are separate entities — a user books on behalf of a passenger.
3. Pricing is static — fare is fixed at FlightInstance creation time.
4. No cancellations, refunds, or seat upgrades.
5. No seat auto-assignment — client provides the seatId.
6. Payment is synchronous — no webhooks or callbacks.

See [DESIGN.md](DESIGN.md) for full architecture and design decisions.
