## Why

The current spa booking flow treats each time slot as if the whole spa can serve only one customer at a time, and it allows unrealistic appointment operations such as selecting past times or completing future appointments. This change makes spa reservations match real operations: one consultant can manage customer communication while the spa can serve multiple customers in parallel according to admin-configured capacity.

## What Changes

- Add admin-managed spa capacity settings for concurrent bookings, slot block size, working hours, closed days, and date-specific capacity overrides.
- Replace single-slot conflict checks with capacity-based booking that reserves every time block covered by a service duration.
- Prevent invalid booking choices, including past times on the current day, closed dates, outside-working-hours slots, and services whose duration exceeds the open window.
- Use Firestore transactions to reserve capacity blocks and create appointments, treatment plans, and treatment sessions atomically.
- Preserve the current product model where consultants handle advice, chat, notes, and treatment tracking, while operational capacity is modeled as pooled spa capacity rather than named specialists.
- Add appointment fields that allow future room or specialist assignment without requiring a specialist management screen in this change.
- Update customer scheduling for later treatment sessions to use the same capacity engine as first bookings.
- Tighten appointment state transitions so future appointments cannot be completed, no-shows require the appointment time plus a grace period, and early arrivals are represented by check-in/service states.
- Ensure admin cancellation and rescheduling release old capacity blocks and reserve new capacity blocks consistently.
- Normalize consultant chat and assignment ownership so treatment journeys do not create duplicate conversations and reassignment updates appointment, plan, session, and chat ownership together.
- Document required Firestore rule behavior for capacity settings, appointments, treatment records, and chat ownership.

## Capabilities

### New Capabilities

- `spa-capacity-booking`: Admin-configured pooled spa capacity, customer slot availability, and transactional capacity reservation for first appointments and later treatment sessions.
- `spa-appointment-lifecycle`: Realistic appointment and treatment-session state transitions for check-in, in-service, completion, no-show, cancellation, and rescheduling.
- `spa-conversation-assignment-consistency`: Consultant assignment and chat-thread ownership rules that keep appointment, treatment plan, session, and conversation records consistent.

### Modified Capabilities

- None. The repository does not currently contain archived baseline specs under `openspec/specs/`; this change defines new spec contracts for the existing spa implementation.

## Impact

- Android models: appointment status metadata, appointment capacity fields, capacity settings/override/block models, and treatment session mapping helpers.
- Customer UI: first spa booking screen and future treatment-session scheduling dialog.
- Consultant UI: schedule cards, detail actions, check-in/service/completion/no-show gates, and chat inbox grouping.
- Admin UI: spa capacity settings, daily capacity overrides, appointment cancellation/rescheduling, reassignment consistency, and operational status display.
- Firestore collections: `appointments`, `treatment_plans`, `treatment_sessions`, `consultation_chat_threads`, `consultation_chat_messages`, and new capacity collections such as `spa_capacity_settings`, `spa_capacity_overrides`, and `appointment_capacity_blocks`.
- Firestore rules/indexes: production rules must enforce customer, consultant, admin, capacity, and assignment boundaries; queries may need indexes for date/block/status/consultant lookups.
