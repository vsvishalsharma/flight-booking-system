# CHANGELOG — Implementation Deviations from Interview Design

This document records every place the implementation differs from the entity model and flow discussed in the interview.
The implementation is the source of truth; these entries exist so the interviewer can see the reasoning behind each delta.

---

## Deviation 1 — Booking owns `seat_id`; Seat has no `booking_id`

| | |
|---|---|
| **Original Interview Design** | `Seat` row carries a `booking_id` FK that points back to the booking currently holding or owning it. |
| **Final Implementation** | `Booking` stores `seat_id` as a FK column. `Seat` has no `booking_id` column. |
| **Reason** | A `Seat` transitions through AVAILABLE → HELD → BOOKED → AVAILABLE independent of any single booking. If `Seat` owned the reference, releasing a failed booking would require nulling `booking_id` on the seat — creating a window where the row is inconsistent. With Booking owning the reference, a failed booking row can be persisted (as FAILED) while the Seat row cleanly transitions back to AVAILABLE with no foreign-key mutation. |
| **Impact** | To find the booking for a seat: `SELECT * FROM bookings WHERE seat_id = ?`. A released seat never has a dangling FK. |

---

## Deviation 2 — Booking owns `passenger_id`; Passenger has no `booking_id`

| | |
|---|---|
| **Original Interview Design** | `Passenger` entity carries a `booking_id` FK, making Passenger a child of Booking. |
| **Final Implementation** | `Booking` stores `passenger_id` as a FK column. `Passenger` has no `booking_id` column. |
| **Reason** | Consistent with Deviation 1: Booking is the owning side of all associations it participates in. Storing `passenger_id` on Booking means the full reservation — who booked, for whom, on which flight, in which seat — is readable from a single `bookings` row without additional joins. |
| **Impact** | To find the passenger for a booking: read `bookings.passenger_id`. The Booking row is the single source of truth for the reservation. |

---

## Deviation 3 — `BookingStatus.CANCELLED` replaced by `FAILED`; `expiredAt` field omitted

| | |
|---|---|
| **Original Interview Design** | `BookingStatus` values: `Pending`, `Confirmed`, `Cancelled`, `Expired`. `Booking` entity has an `expiredat` timestamp field. |
| **Final Implementation** | `BookingStatus` values: `PENDING`, `CONFIRMED`, `FAILED`, `EXPIRED`. No `CANCELLED` status. No `expired_at` column. `EXPIRED` is defined in the enum but is never set (no background expiry job is implemented). |
| **Reason** | `CANCELLED` implies a user-initiated cancellation flow (with a cancellation endpoint and optionally a refund), which is explicitly out of scope per the business assumptions (no cancellation, no refund). `FAILED` is used for the payment-gateway failure path — a system-driven outcome, not a user action. The `expiredAt` field and the expiry job are also out of scope; `EXPIRED` is retained in the enum as a placeholder for that future concern. |
| **Impact** | No cancellation endpoint exists. A booking that cannot complete due to payment failure is stored as `FAILED`. `EXPIRED` is dead code for this submission. |
