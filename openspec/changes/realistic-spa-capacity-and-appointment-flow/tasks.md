## 1. Data Models And Scheduling Helpers

- [x] 1.1 Add capacity setting, date override, and capacity block models with Firestore mapping helpers.
- [x] 1.2 Extend `SpaAppointment` with capacity metadata fields such as capacity units, resource mode, reserved block keys, assigned room/specialist display names, and internal staff notes.
- [x] 1.3 Add appointment status constants and display metadata for `checked_in` and `in_service` while preserving existing statuses.
- [x] 1.4 Add helper functions to calculate working windows, valid start slots, service end times, and required capacity block keys from date, slot, duration, and block size.
- [x] 1.5 Add helper functions to resolve effective capacity for a date from default settings plus date-specific overrides.
- [x] 1.6 Add helper functions to mark slots as available, full, closed, past, or invalid because service duration crosses a working-window boundary.

## 2. Admin Capacity Management

- [x] 2.1 Add an admin capacity management entry point from the admin dashboard.
- [x] 2.2 Build an admin screen for editing default concurrent bookings, block length, working windows, closed weekdays, and booking horizon.
- [x] 2.3 Add admin controls for date-specific capacity overrides and closed-date notes.
- [x] 2.4 Persist capacity settings and overrides to Firestore with loading, save, error, and empty/default states.
- [x] 2.5 Show capacity summary data in admin appointment views, including full slots and remaining capacity where available.

## 3. Capacity Reservation Engine

- [x] 3.1 Implement a shared transaction helper that reads required capacity blocks and rejects reservations when any block is full or closed.
- [x] 3.2 Update the shared helper to create missing capacity block documents with configured capacity when a block is first reserved.
- [x] 3.3 Implement capacity release logic for future cancelled appointments by decrementing all reserved block counters safely.
- [ ] 3.4 Implement reschedule logic that releases old future blocks and reserves new blocks in one transaction.
- [x] 3.5 Add legacy compatibility so existing active appointments without reserved block keys are still considered during availability calculation or are backfilled before capacity-only checks are trusted.

## 4. Customer First Booking Flow

- [x] 4.1 Replace first-booking `startAt` conflict checks with capacity availability checks across the full service duration.
- [x] 4.2 Update the booking calendar and slot grid to disable past current-day slots, closed dates, full slots, and slots whose duration exceeds working hours.
- [x] 4.3 Update single-session booking to reserve capacity blocks and create the pending appointment atomically.
- [x] 4.4 Update treatment-package booking to reserve capacity blocks and create the appointment, treatment plan, and session skeletons atomically.
- [x] 4.5 Show customer-friendly errors when a slot fills during confirmation and require the customer to select another slot.
- [x] 4.6 Preserve existing signed-in booking and package snapshot behavior for active spa packages.
- [x] 4.7 Prevent a customer from creating a second active treatment plan for the same spa package.

## 5. Customer Future Session Scheduling

- [x] 5.1 Replace future treatment-session scheduling conflict checks with the shared capacity engine.
- [x] 5.2 Disable invalid future-session slots using the same past-time, closed-date, duration, and capacity rules as first bookings.
- [x] 5.3 Create the linked appointment and update the treatment session schedule fields in the same reservation transaction.
- [x] 5.4 Prevent customers from scheduling treatment sessions that already have active concrete appointments.
- [x] 5.5 Keep no-show sessions reschedulable without incrementing completed-session count.

## 6. Appointment Lifecycle Actions

- [x] 6.1 Update consultant and admin action gates to show valid transitions for pending, assigned, confirmed, checked-in, in-service, completed, cancelled, no-show, and rescheduled appointments.
- [x] 6.2 Add check-in action for confirmed appointments and store check-in metadata.
- [x] 6.3 Add start-service action for checked-in appointments and store in-service metadata.
- [x] 6.4 Gate completion so future appointments cannot be completed and completed treatment sessions increment plan progress only once.
- [x] 6.5 Gate no-show so it is allowed only after appointment start plus the configured grace period.
- [x] 6.6 Hide normal operational action buttons for completed, cancelled, and no-show appointments.
- [x] 6.7 Update customer/admin/consultant status labels and descriptions for the expanded lifecycle.

## 7. Admin Appointment Operations

- [x] 7.1 Update admin cancellation to release future reserved capacity blocks and preserve cancellation metadata.
- [ ] 7.2 Add or update admin rescheduling UI so admins can select a new valid capacity-backed date and time.
- [ ] 7.3 Ensure rescheduling updates linked treatment session schedule fields and appointment display labels.
- [x] 7.4 Add optional admin fields for internal room name, service specialist name, and staff notes without requiring specialist records.
- [x] 7.5 Ensure admin appointment lists remain usable for legacy appointments that lack capacity metadata.

## 8. Consultant Assignment And Chat Consistency

- [x] 8.1 Keep consultant claim focused on communication ownership and treatment tracking, not capacity reservation.
- [x] 8.2 Normalize new treatment-package chat threads to one canonical thread per treatment plan while preserving appointment-only thread compatibility.
- [x] 8.3 Update consultant chat inbox grouping so one treatment journey appears once even when it has multiple session appointments.
- [ ] 8.4 Update admin reassignment to synchronize consultant fields across appointment, treatment plan, treatment sessions, progress photos, and canonical chat thread.
- [x] 8.5 Ensure previous consultants cannot send new messages after reassignment and current assigned consultants can.
- [x] 8.6 Keep customer chat access tied to the customer-owned appointment or treatment plan.

## 9. Security And Rule Notes

- [x] 9.1 Document Firestore rules needed for admin-only capacity settings and capacity overrides.
- [x] 9.2 Document Firestore rules needed for customer appointment creation, own-appointment reads, and allowed customer cancellations.
- [x] 9.3 Document Firestore rules needed for consultant-owned appointment, treatment, progress-photo, and chat updates.
- [x] 9.4 Document Firestore rules needed to prevent customers or consultants from forging capacity counters, terminal statuses, or reassignment fields.
- [x] 9.5 Note Firestore indexes required for capacity blocks, appointments by date/status, consultant assignment, and treatment-plan chat grouping.

## 10. Verification

- [ ] 10.1 Verify that two or more customers can book the same start time when capacity remains available.
- [ ] 10.2 Verify that a slot is unavailable when any duration block it needs is full.
- [ ] 10.3 Verify that current-day past times cannot be selected or submitted.
- [ ] 10.4 Verify that a service cannot be booked when it would end outside working hours.
- [ ] 10.5 Verify that simultaneous booking attempts cannot exceed configured capacity.
- [ ] 10.6 Verify that cancelling a future appointment releases capacity and rescheduling moves capacity atomically.
- [ ] 10.7 Verify that consultants cannot complete future appointments and cannot mark no-show before the grace period.
- [ ] 10.8 Verify that treatment-session completion increments completed-session count once and no-show does not increment it.
- [ ] 10.9 Verify that later treatment-session scheduling uses the same capacity rules as first booking.
- [ ] 10.10 Verify that consultant chat inbox no longer shows duplicate treatment conversations for each session appointment.
- [ ] 10.11 Verify that admin reassignment updates appointment, treatment plan, sessions, progress photos, and chat ownership consistently.
