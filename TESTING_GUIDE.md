# Testing Guide — Flight Booking System

---

## 1. Automated Tests

### Run all tests

```bash
mvn clean test
```

Docker must be running. Testcontainers spins up a PostgreSQL 16 container automatically for integration tests.

**Expected output**

```
Tests run: 27, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

---

### Unit Tests (23 tests)

All unit tests use Mockito strict stubbing. No database or Spring context is started.

#### SearchServiceTest — 6 tests

| Test | What it verifies |
|------|-----------------|
| `search_returnsExistingInstances_whenInstancesExistForDate` | Returns existing FlightInstances without creating new ones |
| `search_createsFlightInstanceLazily_whenNoInstanceExistsForDate` | Creates snapshot with correct departure time, fare, seat count when none exists |
| `search_snapshotIsImmutable_laterFlightChangesDoNotAffectExistingInstance` | Existing instance retains original schedule after Flight row is mutated |
| `search_throwsResourceNotFoundException_whenSourceAirportNotFound` | Invalid source code throws ResourceNotFoundException |
| `search_throwsResourceNotFoundException_whenDestinationAirportNotFound` | Invalid destination code throws ResourceNotFoundException |
| `search_returnsEmptyList_whenNoFlightsExistOnRoute` | No flights on route returns empty list |

#### SeatServiceTest — 7 tests

| Test | What it verifies |
|------|-----------------|
| `holdSeat_success_whenSeatIsAvailable` | AVAILABLE seat transitions to HELD; holdExpiry set; availableSeats decremented |
| `holdSeat_throwsSeatNotAvailableException_whenSeatIsHeld` | Already-HELD seat (non-expired) throws SeatNotAvailableException |
| `holdSeat_throwsSeatNotAvailableException_whenSeatIsBooked` | BOOKED seat throws SeatNotAvailableException |
| `holdSeat_success_whenHoldIsExpired` | HELD seat with expired holdExpiry can be reclaimed |
| `holdSeat_throwsResourceNotFoundException_whenSeatNotFound` | Non-existent seatId throws ResourceNotFoundException |
| `confirmSeat_success_setsStatusToBooked` | HELD seat becomes BOOKED; holdExpiry cleared; availableSeats NOT incremented |
| `releaseSeat_success_restoresSeatToAvailable` | HELD seat becomes AVAILABLE; holdExpiry cleared; availableSeats incremented |

#### PaymentServiceTest — 4 tests

| Test | What it verifies |
|------|-----------------|
| `processPayment_success_whenGatewaySucceeds` | Gateway success → Payment.status = SUCCESS, transactionId persisted |
| `processPayment_failed_whenGatewayFails` | Gateway failure → Payment.status = FAILED, transactionId = null |
| `processPayment_idempotent_returnsSamePaymentOnDuplicateKey` | Duplicate key → existing Payment returned, gateway NOT called again |
| `processPayment_idempotent_preservesFailedPayment` | Duplicate key for a failed payment → FAILED Payment returned, no re-charge |

#### BookingServiceTest — 6 tests

| Test | What it verifies |
|------|-----------------|
| `initiateBooking_success_whenAllStepsSucceed` | Full flow: holdSeat → passenger → booking(PENDING) → payment(SUCCESS) → confirmSeat → CONFIRMED |
| `initiateBooking_bookingFailed_whenPaymentFails` | Payment failure → releaseSeat called, booking status = FAILED |
| `initiateBooking_throwsSeatNotAvailableException_whenSeatAlreadyBooked` | Unavailable seat → exception propagated, no Passenger or Booking created |
| `initiateBooking_throwsResourceNotFoundException_whenUserNotFound` | Invalid userId → exception before any seat logic |
| `initiateBooking_throwsResourceNotFoundException_whenFlightInstanceNotFound` | Invalid flightInstanceId → exception before seat hold |
| `initiateBooking_usesProvidedIdempotencyKey_forPayment` | Client X-Idempotency-Key is forwarded to PaymentService unchanged |

---

### Integration Tests (4 tests)

Uses Testcontainers (PostgreSQL 16). Flyway runs both migrations on each test run. Full Spring context is loaded.

| Test | What it verifies |
|------|-----------------|
| `fullBookingFlow_searchThenBook_resultsInConfirmedBooking` | End-to-end: search → book → DB state (seat BOOKED, payment SUCCESS, booking CONFIRMED, correct idempotency key stored) |
| `searchFlight_lazyCreatesFlightInstance_whenNoneExistForDate` | Search for a date with no seeded instance triggers lazy creation; response includes the new instance |
| `initiateBooking_returns409_whenSeatAlreadyBooked` | Booking the same seat twice returns HTTP 409 on the second request |
| `searchFlight_returns404_whenAirportCodeInvalid` | Search with unknown airport code returns HTTP 404 |

---

## 2. Manual API Testing (curl)

### Prerequisites

```bash
docker compose up --build -d
docker compose logs -f app   # wait for "Started FlightBookingApplication"
```

App: `http://localhost:8082`
DB: `localhost:5433` (user: postgres, password: postgres, db: flightbooking)

---

### 2.1 Search — existing FlightInstance

```bash
D7=$(date -d '+7 days' +%Y-%m-%d)
curl -s "http://localhost:8082/flights/search?source=DEL&destination=BLR&travelDate=$D7" | python3 -m json.tool
```

Expected: HTTP 200, `flightNumber: AI202`, `availableSeats: 5`, `fare: 5500.0`

---

### 2.2 Search — lazy FlightInstance creation

```bash
D90=$(date -d '+90 days' +%Y-%m-%d)
curl -s "http://localhost:8082/flights/search?source=DEL&destination=BLR&travelDate=$D90" | python3 -m json.tool
```

Expected: HTTP 200, new FlightInstance created with `availableSeats: 5`

Verify in DB:

```bash
docker exec flightbooking-postgres psql -U postgres -d flightbooking -c \
  "SELECT id, travel_date, available_seats FROM flight_instances WHERE travel_date='$D90';"
```

---

### 2.3 Booking flow — happy path

```bash
# Step 1: find an AVAILABLE seat
docker exec flightbooking-postgres psql -U postgres -d flightbooking -c \
  "SELECT id, seat_number, status FROM seats WHERE flight_instance_id=1 AND status='AVAILABLE';"

# Step 2: book it (replace SEAT_ID with a value from step 1)
curl -s -X POST http://localhost:8082/bookings/initiate \
  -H "Content-Type: application/json" \
  -H "X-Idempotency-Key: test-happy-path-001" \
  -d '{
    "userId": 1,
    "flightInstanceId": 1,
    "seatId": SEAT_ID,
    "passenger": {"name": "Vishal Sharma", "age": 30, "gender": "Male"}
  }' | python3 -m json.tool
```

Expected: `bookingStatus: CONFIRMED`, `payment.status: SUCCESS`

Verify state transitions:

```bash
# Seat must be BOOKED
docker exec flightbooking-postgres psql -U postgres -d flightbooking -c \
  "SELECT id, status FROM seats WHERE id=SEAT_ID;"

# Booking must be CONFIRMED
docker exec flightbooking-postgres psql -U postgres -d flightbooking -c \
  "SELECT id, status, passenger_id FROM bookings ORDER BY id DESC LIMIT 1;"

# Payment must be SUCCESS with idempotency key stored
docker exec flightbooking-postgres psql -U postgres -d flightbooking -c \
  "SELECT id, status, gateway_transaction_id, idempotency_key FROM payments ORDER BY id DESC LIMIT 1;"

# availableSeats must be decremented by 1
docker exec flightbooking-postgres psql -U postgres -d flightbooking -c \
  "SELECT available_seats FROM flight_instances WHERE id=1;"
```

---

### 2.4 Failure — seat already booked (409)

```bash
# Re-send the same booking with the same SEAT_ID (now BOOKED)
curl -s -w "\nHTTP %{http_code}" -X POST http://localhost:8082/bookings/initiate \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"flightInstanceId":1,"seatId":SEAT_ID,"passenger":{"name":"Other","age":25,"gender":"Male"}}'
```

Expected: HTTP 409, `"Seat ... is not available (status: BOOKED)"`

---

### 2.5 Failure — invalid user (404)

```bash
curl -s -w "\nHTTP %{http_code}" -X POST http://localhost:8082/bookings/initiate \
  -H "Content-Type: application/json" \
  -d '{"userId":999,"flightInstanceId":1,"seatId":2,"passenger":{"name":"Ghost","age":20,"gender":"Male"}}'
```

Expected: HTTP 404, `"User not found: 999"`

---

### 2.6 Failure — invalid FlightInstance (404)

```bash
curl -s -w "\nHTTP %{http_code}" -X POST http://localhost:8082/bookings/initiate \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"flightInstanceId":9999,"seatId":2,"passenger":{"name":"Test","age":25,"gender":"Male"}}'
```

Expected: HTTP 404, `"FlightInstance not found: 9999"`

---

### 2.7 Failure — invalid airport in search (404)

```bash
curl -s -w "\nHTTP %{http_code}" \
  "http://localhost:8082/flights/search?source=XYZ&destination=BLR&travelDate=$(date -d '+7 days' +%Y-%m-%d)"
```

Expected: HTTP 404, `"Airport not found: XYZ"`

---

### 2.8 Failure — missing required fields (400)

```bash
curl -s -w "\nHTTP %{http_code}" -X POST http://localhost:8082/bookings/initiate \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"flightInstanceId":1}'
```

Expected: HTTP 400, message lists `seatId is required` and `passenger is required`

---

### 2.9 Idempotency — same key sent twice

```bash
KEY="idem-test-$(date +%s)"

# First call — book seat 6 (flightInstance 2)
curl -s -X POST http://localhost:8082/bookings/initiate \
  -H "Content-Type: application/json" \
  -H "X-Idempotency-Key: $KEY" \
  -d '{"userId":2,"flightInstanceId":2,"seatId":6,"passenger":{"name":"Abhirub Acharya","age":28,"gender":"Male"}}' \
  | python3 -m json.tool

# Second call — same key, seat is now BOOKED; second request rejected at seat hold
curl -s -w "\nHTTP %{http_code}" -X POST http://localhost:8082/bookings/initiate \
  -H "Content-Type: application/json" \
  -H "X-Idempotency-Key: $KEY" \
  -d '{"userId":2,"flightInstanceId":2,"seatId":6,"passenger":{"name":"Abhirub Acharya","age":28,"gender":"Male"}}'

# Verify only ONE payment row exists for this key
docker exec flightbooking-postgres psql -U postgres -d flightbooking -c \
  "SELECT COUNT(*), status FROM payments WHERE idempotency_key='$KEY' GROUP BY status;"
```

Expected: first call CONFIRMED, second call HTTP 409, payment count = 1

---

### 2.10 Snapshot immutability

```bash
D14=$(date -d '+14 days' +%Y-%m-%d)
D100=$(date -d '+100 days' +%Y-%m-%d)

# Record current snapshot for flightInstance 2 (AI202, +14 days)
docker exec flightbooking-postgres psql -U postgres -d flightbooking -c \
  "SELECT id, departure_time, fare FROM flight_instances WHERE id=2;"

# Change Flight AI202 defaults
docker exec flightbooking-postgres psql -U postgres -d flightbooking -c \
  "UPDATE flights SET default_departure_time='06:00:00', default_fare=9999.00 WHERE flight_number='AI202';"

# Existing date — must still return 22:00 / 5500 (snapshot preserved)
curl -s "http://localhost:8082/flights/search?source=DEL&destination=BLR&travelDate=$D14" \
  | python3 -c "import json,sys; fi=json.load(sys.stdin)['data'][0]; print('departure:', fi['departureTime'], '| fare:', fi['fare'])"

# New date — must return 06:00 / 9999 (new snapshot picks up updated defaults)
curl -s "http://localhost:8082/flights/search?source=DEL&destination=BLR&travelDate=$D100" \
  | python3 -c "import json,sys; fi=json.load(sys.stdin)['data'][0]; print('departure:', fi['departureTime'], '| fare:', fi['fare'])"

# Restore
docker exec flightbooking-postgres psql -U postgres -d flightbooking -c \
  "UPDATE flights SET default_departure_time='22:00:00', default_fare=5500.00 WHERE flight_number='AI202';"
```

Expected: existing date shows `22:00:00 / 5500.0`, new date shows `06:00:00 / 9999.0`

---

### 2.11 Concurrency — two users, same seat

```bash
# Pick an AVAILABLE seat — check first
docker exec flightbooking-postgres psql -U postgres -d flightbooking -c \
  "SELECT id, seat_number, status FROM seats WHERE flight_instance_id=3 AND status='AVAILABLE' LIMIT 1;"

# Run both requests in parallel (replace SEAT_ID)
curl -s -X POST http://localhost:8082/bookings/initiate \
  -H "Content-Type: application/json" \
  -H "X-Idempotency-Key: concurrent-a-$(date +%s)" \
  -d '{"userId":1,"flightInstanceId":3,"seatId":SEAT_ID,"passenger":{"name":"User A","age":30,"gender":"Male"}}' \
  > /tmp/user_a.json &

curl -s -X POST http://localhost:8082/bookings/initiate \
  -H "Content-Type: application/json" \
  -H "X-Idempotency-Key: concurrent-b-$(date +%s)" \
  -d '{"userId":2,"flightInstanceId":3,"seatId":SEAT_ID,"passenger":{"name":"User B","age":25,"gender":"Female"}}' \
  > /tmp/user_b.json &

wait

echo "User A:" && python3 -c "import json,sys; r=json.load(open('/tmp/user_a.json')); print(r.get('data',{}).get('bookingStatus') or r.get('message'))"
echo "User B:" && python3 -c "import json,sys; r=json.load(open('/tmp/user_b.json')); print(r.get('data',{}).get('bookingStatus') or r.get('message'))"

# Verify exactly one CONFIRMED booking for that seat
docker exec flightbooking-postgres psql -U postgres -d flightbooking -c \
  "SELECT id, user_id, status FROM bookings WHERE seat_id=SEAT_ID;"
```

Expected: one `CONFIRMED`, one `"Seat ... is not available (status: BOOKED)"`. Exactly one booking row.

---

## 3. Seed Data Reference

| Entity | Values |
|--------|--------|
| Airports | DEL, BLR, BOM, MAA |
| Flights | AI202 DEL→BLR ₹5500 · AI303 DEL→BOM ₹4200 · AI404 BOM→MAA ₹3800 |
| FlightInstances | IDs 1–5: AI202×2, AI303×2, AI404×1 (CURRENT_DATE +7 and +14) |
| Seats | IDs 1–25: 5 per pre-seeded instance (1A 1B 1C 2A 2B) |
| Users | ID 1: Vishal Sharma · ID 2: Abhirub Acharya · ID 3: Priya Patel |
