# Test Report — Flight Booking System

## Summary

| Category         | Tests | Passed | Failed |
|------------------|-------|--------|--------|
| Unit Tests       | 24    | 24     | 0      |
| Integration Tests| 5     | 5      | 0      |
| **Total**        | **29**| **29** | **0**  |

---

## Unit Tests

### SearchServiceTest

| # | Test Method | Purpose | Expected Result | Actual Result | Status |
|---|-------------|---------|-----------------|---------------|--------|
| 1 | `search_returnsExistingInstances_whenInstancesExistForDate` | Returns existing FlightInstances when found for the requested date | List of FlightInstanceResponse with correct flightNumber, fare, and availableSeats | FlightInstance returned with AI202, ₹5500, 5 seats | ✅ PASS |
| 2 | `search_createsFlightInstanceLazily_whenNoInstanceExistsForDate` | Creates a new FlightInstance snapshot when none exists for the date | FlightInstance saved with Flight's defaultDepartureTime, defaultFare, totalSeats; Seats generated | Instance created with 22:00 departure, ₹5500, 5 seats; `seatRepository.saveAll` called | ✅ PASS |
| 3 | `search_existingFlightInstance_departureTimeFrozenAtSnapshotCreation` | Existing FlightInstance retains original schedule after Flight is modified | Response shows 22:00 even after parent Flight mutated to 06:00 | Departure returned as 22:00; confirmed ≠ Flight's 06:00 | ✅ PASS |
| 4 | `search_throwsResourceNotFoundException_whenSourceAirportNotFound` | Invalid source airport code throws ResourceNotFoundException | ResourceNotFoundException with "XYZ" in message | Exception thrown with message containing "XYZ" | ✅ PASS |
| 5 | `search_throwsResourceNotFoundException_whenDestinationAirportNotFound` | Invalid destination airport code throws ResourceNotFoundException | ResourceNotFoundException with "ABC" in message | Exception thrown with message containing "ABC" | ✅ PASS |
| 6 | `search_returnsEmptyList_whenNoFlightsExistOnRoute` | No flights on route returns empty list | Empty list returned | Empty list returned | ✅ PASS |

---

### SeatServiceTest

| # | Test Method | Purpose | Expected Result | Actual Result | Status |
|---|-------------|---------|-----------------|---------------|--------|
| 7 | `holdSeat_success_whenSeatIsAvailable` | Holding an AVAILABLE seat transitions it to HELD | Seat status = HELD, holdExpiry set to future, availableSeats decremented | Status = HELD, holdExpiry > now, decrementAvailableSeats called | ✅ PASS |
| 8 | `holdSeat_throwsSeatNotAvailableException_whenSeatIsHeld` | Cannot hold a seat that is already HELD | SeatNotAvailableException with seat number in message | Exception thrown with "1B" | ✅ PASS |
| 9 | `holdSeat_throwsSeatNotAvailableException_whenSeatIsBooked` | Cannot hold a BOOKED seat | SeatNotAvailableException with seat number in message | Exception thrown with "1C" | ✅ PASS |
| 10 | `holdSeat_success_whenHoldIsExpired` | A HELD seat with expired holdExpiry can be reclaimed | Seat transitions to HELD with fresh holdExpiry | Status = HELD, holdExpiry refreshed to future | ✅ PASS |
| 11 | `holdSeat_throwsResourceNotFoundException_whenSeatNotFound` | Hold request for non-existent seatId throws ResourceNotFoundException | ResourceNotFoundException with "99" in message | Exception thrown with "99" | ✅ PASS |
| 12 | `confirmSeat_success_setsStatusToBooked` | Confirming a HELD seat sets status to BOOKED | Status = BOOKED, holdExpiry = null, availableSeats NOT incremented | Status = BOOKED, holdExpiry null | ✅ PASS |
| 13 | `releaseSeat_success_restoresSeatToAvailable` | Releasing a HELD seat restores it to AVAILABLE | Status = AVAILABLE, holdExpiry = null, availableSeats incremented | Status = AVAILABLE, holdExpiry null, incrementAvailableSeats called | ✅ PASS |

---

### PaymentServiceTest

| # | Test Method | Purpose | Expected Result | Actual Result | Status |
|---|-------------|---------|-----------------|---------------|--------|
| 14 | `processPayment_success_whenGatewaySucceeds` | Payment succeeds when gateway returns success | Payment.status = SUCCESS, gatewayTransactionId set, saved to DB | Status = SUCCESS, txnId = "TXN123", bookingId and amount persisted | ✅ PASS |
| 15 | `processPayment_failed_whenGatewayFails` | Payment fails when gateway returns failure | Payment.status = FAILED, gatewayTransactionId = null | Status = FAILED, txnId null | ✅ PASS |
| 16 | `processPayment_idempotent_returnsSamePaymentOnDuplicateKey` | Duplicate idempotency key returns existing payment without re-charging | Existing Payment returned, gateway NOT called, no new DB save | Existing Payment (id=42) returned, verify(gateway, never()) passes | ✅ PASS |
| 17 | `processPayment_idempotent_preservesFailedPayment` | Idempotency key for a failed payment returns the same FAILED payment | FAILED Payment returned without calling gateway again | Existing FAILED Payment returned | ✅ PASS |

---

### BookingServiceTest

| # | Test Method | Purpose | Expected Result | Actual Result | Status |
|---|-------------|---------|-----------------|---------------|--------|
| 18 | `initiateBooking_success_whenAllStepsSucceed` | Full successful booking flow: hold → create → pay → confirm | BookingStatus = CONFIRMED, PaymentStatus = SUCCESS, confirmSeat called | Status = CONFIRMED, payment SUCCESS, seatService.confirmSeat(1L) verified | ✅ PASS |
| 19 | `initiateBooking_bookingFailed_whenPaymentFails` | Payment failure results in FAILED booking and seat release | BookingStatus = FAILED, releaseSeat called, confirmSeat NOT called | Status = FAILED, releaseSeat(1L) verified, confirmSeat never called | ✅ PASS |
| 20 | `initiateBooking_throwsSeatNotAvailableException_whenSeatAlreadyBooked` | SeatNotAvailableException propagates; no Booking or Passenger created | SeatNotAvailableException thrown, no DB writes for Passenger or Booking | Exception thrown, passengerRepository.save never called | ✅ PASS |
| 21 | `initiateBooking_throwsResourceNotFoundException_whenUserNotFound` | Invalid userId throws ResourceNotFoundException before any seat logic | ResourceNotFoundException; seatService.holdSeat never called | Exception with "99" thrown, holdSeat never called | ✅ PASS |
| 22 | `initiateBooking_throwsResourceNotFoundException_whenFlightInstanceNotFound` | Invalid flightInstanceId throws ResourceNotFoundException before seat hold | ResourceNotFoundException; seatService.holdSeat never called | Exception with "99" thrown, holdSeat never called | ✅ PASS |
| 23 | `initiateBooking_usesProvidedIdempotencyKey_forPayment` | Client-provided X-Idempotency-Key is forwarded to PaymentService | PaymentService called with exact client-provided key | paymentService.processPayment called with "my-custom-key" | ✅ PASS |
| 24 | `initiateBooking_throwsSeatNotAvailableException_whenSeatBelongsToDifferentFlightInstance` | Seat from a different FlightInstance is rejected with 409 before any Passenger or Booking is created | SeatNotAvailableException thrown, no DB writes | Exception thrown with "does not belong" message | ✅ PASS |

---

## Integration Tests

> Tests use Testcontainers (PostgreSQL 16) with Flyway migrations applied automatically.

| # | Test Method | Purpose | Expected Result | Actual Result | Status |
|---|-------------|---------|-----------------|---------------|--------|
| IT-1 | `fullBookingFlow_searchThenBook_resultsInConfirmedBooking` | End-to-end: search flight → initiate booking → verify DB state | HTTP 200 with CONFIRMED booking; Seat = BOOKED in DB; Payment = SUCCESS in DB with correct idempotency key | All assertions pass; DB state consistent | ✅ PASS |
| IT-2 | `searchFlight_lazyCreatesFlightInstance_whenNoneExistForDate` | Search for a date with no seeded data triggers lazy FlightInstance creation | HTTP 200 with at least one result matching the requested date | FlightInstance created and returned for `now + 60 days` | ✅ PASS |
| IT-3 | `initiateBooking_returns409_whenSeatAlreadyBooked` | Booking same seat twice results in 409 Conflict on second attempt | Second booking returns HTTP 409 | HTTP 409 returned for duplicate seat booking | ✅ PASS |
| IT-4 | `searchFlight_returns404_whenAirportCodeInvalid` | Search with non-existent airport code returns 404 | HTTP 404 with error message | HTTP 404 returned for airport "XXX" | ✅ PASS |
| IT-5 | `initiateBooking_returns409_whenSeatBelongsToDifferentFlightInstance` | Booking a seat from a different FlightInstance returns 409 | HTTP 409 Conflict | HTTP 409 returned; no booking or payment created | ✅ PASS |

---

## Test Coverage by Scenario

| Scenario | Test Method(s) |
|----------|----------------|
| Successful booking (full flow) | `initiateBooking_success_whenAllStepsSucceed`, `fullBookingFlow_searchThenBook_resultsInConfirmedBooking` |
| Seat already booked | `holdSeat_throwsSeatNotAvailableException_whenSeatIsBooked`, `initiateBooking_throwsSeatNotAvailableException_whenSeatAlreadyBooked`, `initiateBooking_returns409_whenSeatAlreadyBooked` |
| Payment failure | `processPayment_failed_whenGatewayFails`, `initiateBooking_bookingFailed_whenPaymentFails` |
| Duplicate payment (idempotency) | `processPayment_idempotent_returnsSamePaymentOnDuplicateKey`, `processPayment_idempotent_preservesFailedPayment`, `initiateBooking_usesProvidedIdempotencyKey_forPayment` |
| FlightInstance snapshot behaviour | `search_snapshotIsImmutable_laterFlightChangesDoNotAffectExistingInstance`, `search_createsFlightInstanceLazily_whenNoInstanceExistsForDate`, `searchFlight_lazyCreatesFlightInstance_whenNoneExistForDate` |
| Expired seat hold can be reclaimed | `holdSeat_success_whenHoldIsExpired` |
| Resource not found | `search_throwsResourceNotFoundException_whenSourceAirportNotFound`, `initiateBooking_throwsResourceNotFoundException_whenUserNotFound`, `initiateBooking_throwsResourceNotFoundException_whenFlightInstanceNotFound`, `searchFlight_returns404_whenAirportCodeInvalid` |
