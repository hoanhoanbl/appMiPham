## Context

The app is a Kotlin Jetpack Compose Android app that reads and writes Firebase Auth and Firestore directly from UI screens. Current spa work already includes `spa_packages`, `appointments`, consultant role routing, `treatment_plans`, `treatment_sessions`, consultation chat, and progress photos. The current booking logic treats a slot as globally occupied when any active appointment has the same `startAt`, so the spa behaves as if only one customer can be served at a time.

The desired business model is different: the app has one consultant workflow for advice, chat, notes, and treatment tracking, while the spa may have multiple rooms or service specialists working in parallel. The user does not want a specialist-facing app or a full staff roster yet, so operational capacity should be modeled as pooled spa capacity controlled by admins.

## Goals / Non-Goals

**Goals:**

- Let admins configure how many customers the spa can serve concurrently.
- Let admins override capacity or close booking for specific dates.
- Show customers only valid booking slots based on working hours, current time, package duration, and remaining capacity.
- Reserve every time block covered by a service duration, not only the appointment start time.
- Use transactions so capacity reservation and appointment/treatment creation do not leave partial or overbooked records.
- Apply the same capacity logic to first bookings and later treatment-session scheduling.
- Keep consultants responsible for communication and treatment tracking, not for determining global spa capacity.
- Add realistic appointment transitions for check-in, in-service, completion, no-show, cancellation, and rescheduling.
- Keep chat and consultant assignment consistent across appointment, plan, session, and thread records.

**Non-Goals:**

- Do not add a specialist-facing app, staff time clock, payroll, or detailed shift scheduling.
- Do not require customers to choose a specific room or specialist.
- Do not create a full `specialists` or `rooms` management feature in this change.
- Do not add payment, deposits, penalties, prepaid session deduction, or no-show billing.
- Do not add push notifications, SMS, or email reminders.
- Do not introduce a backend service or Cloud Functions dependency in this change.

## Decisions

### Model operational capacity as pooled spa capacity

Use admin-configured pooled capacity instead of named specialists or rooms. A default capacity record controls `defaultConcurrentBookings`, `slotMinutes`, working windows, closed weekdays, and booking horizon. Date override records can close a date or change its capacity.

This matches the current product need: the spa can serve two or three customers in parallel without requiring screens for each service specialist. Appointment records should still include optional fields such as `capacityUnits`, `resourceMode`, `assignedRoomName`, `assignedSpecialistName`, and `internalStaffNote` so admins can write operational notes and a later change can introduce real room/specialist entities without rewriting historical data.

Alternative considered: create `specialists` and auto-assign one at booking time. That would require skills, shifts, absences, package eligibility, reassignment, and conflict handling for each specialist. It is more realistic for a large spa, but too large for this app's current scope.

### Reserve duration blocks instead of start slots

Convert every appointment into fixed-size capacity blocks. With 30-minute blocks, a 90-minute appointment from 09:00 to 10:30 consumes blocks at 09:00, 09:30, and 10:00. A slot is available only when every block required by the service has remaining capacity.

Alternative considered: continue checking only `startAt`. That allows overlapping services such as 09:00-10:30 and 09:30-10:00 to coexist incorrectly or prevents valid parallel bookings depending on the data.

### Use Firestore transactions for capacity reservation

Booking should run in one transaction for capacity blocks and the appointment/treatment documents. The transaction reads all required `appointment_capacity_blocks`, rejects the booking if any block is full or closed, increments `bookedCount` on each block, then creates the appointment. For treatment packages, the same transaction creates the treatment plan and session skeletons.

Alternative considered: pre-check capacity with a query and then batch write. That is simpler but still allows race conditions when two customers confirm the same available capacity at the same time.

### Keep capacity release explicit

Cancellation, rescheduling, no-show policy changes, or admin overrides should update capacity blocks intentionally. Terminal statuses such as `cancelled` release capacity. `completed` and `no_show` do not release capacity retroactively because the time has already passed. Rescheduling releases old future blocks and reserves new blocks in one transaction.

Alternative considered: derive booked counts by querying all active appointments every time. That avoids counters but creates slow queries, race-prone checks, and complicated overlap logic on mobile clients.

### Add a realistic appointment lifecycle

Use appointment states that reflect real service operations:

```text
pending -> assigned -> confirmed -> checked_in -> in_service -> completed
confirmed -> cancelled
confirmed -> rescheduled
confirmed -> no_show
```

`completed` is allowed only when the appointment has started or is in service. `no_show` is allowed only after the appointment start plus a grace period. Early arrivals should be represented by `checked_in`, not `completed`.

Alternative considered: keep `assigned/confirmed -> completed` as a direct action. That is fast, but it permits future appointments to be completed and hides important operational exceptions.

### Keep consultant assignment separate from pooled service capacity

Consultants continue to claim appointments, chat with customers, write notes, and operate treatment plans. Consultant assignment does not reduce or define booking capacity. Admins may optionally record internal service details, but the app does not need a specialist account for that work.

Alternative considered: make consultant assignment equal to service capacity. That is what the current mental model accidentally suggests, but it is wrong for a spa where one consultant can handle multiple customer journeys while several specialists perform services.

### Normalize conversation ownership by treatment journey

For multi-session treatment packages, use one canonical chat thread per treatment plan or active journey. Appointment-only services can continue using appointment-based threads for compatibility. Consultant chat inbox should not show several duplicate rows for the same treatment journey, and reassignment should update all ownership fields together.

Alternative considered: keep one thread per appointment. That creates duplicate conversations for the same customer and treatment plan, especially when later sessions are scheduled.

## Risks / Trade-offs

- More Firestore writes per booking -> Limit block granularity to 30 minutes, keep the booking horizon small, and reserve only blocks covered by the selected service duration.
- Client-side transactions are still not a security boundary -> Firestore rules must enforce who can change capacity settings, appointments, treatment records, and chat ownership.
- Existing appointments have no capacity block records -> Treat legacy active appointments as read-only occupancy during migration or backfill blocks before enabling capacity booking.
- Capacity counters can drift if manual edits bypass the booking engine -> Route admin cancel/reschedule through shared helpers and add a lightweight audit/check screen later if needed.
- Pooled capacity does not know which specialist actually performed the service -> Store optional internal room/specialist note fields and leave detailed specialist scheduling for a future change.
- More appointment statuses increase UI complexity -> Centralize status metadata and gate visible actions by status/time.

## Migration Plan

1. Add capacity models, status constants, and helper functions for working windows, block generation, availability checks, and transaction payloads.
2. Add default capacity settings and an admin UI for editing concurrent capacity, working windows, closed weekdays, and date overrides.
3. Backfill or compatibility-read current active appointments into capacity calculations before relying only on `appointment_capacity_blocks`.
4. Replace first booking conflict checks with transactional capacity reservation and treatment creation.
5. Replace future treatment-session scheduling with the same capacity reservation flow.
6. Update customer slot UI to display available, full, closed, and past-time states.
7. Update consultant appointment actions to use realistic lifecycle gates.
8. Update admin cancel/reschedule/reassignment flows to release/reserve capacity and keep related ownership records consistent.
9. Normalize chat inbox grouping for treatment plans while preserving appointment-only thread compatibility.
10. Document Firestore rule requirements and required indexes.

Rollback: hide the capacity admin entry point, stop writing new capacity blocks, and fall back to the existing appointment list display. Existing appointments remain readable because appointment fields are additive.

## Open Questions

- What default concurrent capacity should seed a fresh project: `2` or `3`?
- Should date-specific overrides be created only by admins, or should the app auto-create an override when an admin closes a date from the appointment calendar?
- Should legacy active appointments be backfilled into `appointment_capacity_blocks`, or should availability temporarily combine block counters with appointment overlap queries?
