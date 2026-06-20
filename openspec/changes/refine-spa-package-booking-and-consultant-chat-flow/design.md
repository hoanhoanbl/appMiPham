## Context

The app is a Kotlin Jetpack Compose Android app using Firebase Auth and Firestore directly from UI screens. Recent spa changes added admin-managed `spa_packages`, customer `appointments`, consultant role support, `treatment_plans`, `treatment_sessions`, consultation chat, and progress photo records.

The current implementation still has two important workflow mismatches:

- Multi-session treatment plans are created from the consultant appointment detail screen after the consultant claims an appointment.
- The consultant chat inbox opens the combined appointment detail screen, so message conversations are mixed with notes, plan creation, treatment sessions, and session actions.

The desired product model is package-first. Admins define complete spa packages with a fixed session count and policy. Customers book that package. Consultants accept and operate the booked journey rather than designing the package structure later.

## Goals / Non-Goals

**Goals:**
- Treat admin-created spa packages as complete sellable packages.
- Create treatment plans and session skeletons at booking time for multi-session packages.
- Keep the first booking as the concrete appointment for session 1.
- Let customers schedule later sessions from their treatment plan detail.
- Assign the treatment plan, sessions, and chat ownership when a consultant claims the first appointment.
- Remove the normal consultant UI path for creating custom treatment plans or changing session count.
- Split consultant chat into a dedicated message-only screen.
- Add a customer profile/history screen that opens intentionally from the chat header or customer name.
- Preserve existing simple single-session spa appointments.

**Non-Goals:**
- Do not add payment, deposits, package purchase ledgers, prepaid session deduction, or penalties for no-shows.
- Do not add push notifications, SMS, email reminders, or real-time staff availability management.
- Do not add customer approval/rejection of treatment plans.
- Do not let consultants edit admin-defined package session count in the normal flow.
- Do not replace Firestore or add a backend service in this change.

## Decisions

### Create treatment plans at booking time

When a customer books an active multi-session spa package, the booking operation should create:

1. An `appointments` record for the selected first visit.
2. A `treatment_plans` record that copies package/customer data and starts with `status = "waiting_consultant"` or an equivalent pre-assignment status.
3. `treatment_sessions` records numbered `1..sessionCount`.
4. Session 1 linked to the first appointment with concrete schedule fields.
5. Sessions 2..N created without schedule fields and with `status = "unscheduled"`.

This lets customers immediately see the full package journey after booking and avoids a consultant-owned plan creation step.

Alternative considered: create the plan only when a consultant claims the first appointment. That keeps booking writes smaller, but it hides the package journey from the customer until staff action and preserves the wrong mental model that consultants create plans.

### Keep package snapshots on plans and sessions

Treatment plans should copy package fields such as name, price, category, session count, per-session duration, suggested interval, photo policy, and photo guide at booking time. This preserves the customer journey even if an admin later edits the original package.

Alternative considered: always read the live `spa_packages` document from plan/session screens. That risks changing historical bookings when admin content changes.

### Introduce `unscheduled` treatment sessions

Future sessions exist as part of the purchased/booked package but should not look scheduled until the customer chooses a date/time. Add `TreatmentSessionStatus.UNSCHEDULED` and metadata for display.

`scheduled` should mean the session has concrete date/time fields or is linked to an appointment. Existing records that have `scheduledStartAt = 0` can be displayed as `unscheduled` for compatibility, even before migration rewrites them.

Alternative considered: keep using `scheduled` with empty schedule fields. That is ambiguous and causes UI copy like "scheduled" for sessions that are actually waiting for customer scheduling.

### Assign plan ownership when consultant claims the appointment

Consultant claim should atomically update the appointment and related treatment plan/session records for that booking:

- `appointment.consultantId`, `consultantEmail`, `consultantName`, `status = assigned`
- `treatment_plan.consultantId`, `consultantEmail`, `consultantName`, `status = active`
- future `treatment_sessions.consultantId` for query and authorization convenience
- `consultation_chat_threads/<threadId>` with participant and treatment context

The thread id should remain stable for existing data. For package treatments, prefer the treatment plan id as the long-term conversation context when possible, while preserving appointment id compatibility where existing code expects it.

Alternative considered: assign only appointments and let sessions infer consultant through the plan. That reduces writes but makes consultant session queries and Firestore rules harder.

### Customer schedules later sessions one at a time

Later sessions should become concrete appointments only when the customer selects a valid date/time from the treatment plan screen. The scheduling operation should:

- reject occupied active slots;
- create an appointment for that session;
- update the session schedule fields, appointment id, and status to `scheduled`;
- preserve consultant assignment if the plan already has one.

This keeps the operational appointment list limited to actual visits.

Alternative considered: pre-create appointments for all sessions using the suggested interval. That creates fragile schedules before the customer has chosen real times.

### Consultant chat becomes message-only

The consultant dashboard chat tab should navigate to a new dedicated consultant chat screen. That screen should contain only:

- compact customer/conversation header;
- message list;
- composer/send action;
- optional entry point to customer profile/history.

It should not show treatment plan creation, appointment status controls, session cards, progress photo upload, or consultation note panels.

Alternative considered: keep one large appointment detail screen and scroll to chat. That is the current source of confusion and makes chat feel like a secondary subsection instead of a conversation.

### Customer profile/history is a separate screen

Tapping the customer name/avatar in chat should open a profile/history screen with:

- customer name, email, phone;
- active treatment plans;
- appointment history;
- no-show history/count;
- progress-photo summary or links where available.

This provides consultant context without crowding the chat interface.

Alternative considered: put customer history above the chat. That makes every conversation heavier and repeats the current problem in another shape.

## Risks / Trade-offs

- More writes at booking time -> Use batched writes or a transaction so appointment, plan, and sessions stay consistent.
- Existing data may have plans created by consultants -> Keep readers compatible with existing records and hide plan creation UI only for normal package flows.
- Chat thread id compatibility can be tricky -> Preserve appointment-id based lookup for existing records and link threads to treatment plan id when present.
- `unscheduled` status changes assumptions in UI -> Update all status labels and action gates, and treat empty schedules as unscheduled for legacy records.
- Firestore rules may lag app-side changes -> Document required rule behavior and keep app guards, but treat deployed Firestore rules as mandatory for production.
- Booking-time plan creation may fail after appointment creation if not atomic -> Use batched writes/transactions and show a failure snackbar instead of creating partial visible records.

## Migration Plan

1. Add `unscheduled` treatment session status metadata and display compatibility for existing sessions with no concrete schedule.
2. Move multi-session plan/session creation from consultant detail into the customer booking operation.
3. Update package detail and booking copy to say the package creates a complete treatment plan immediately.
4. Update consultant claim to assign the existing plan/sessions and create or update the chat thread.
5. Remove or hide consultant treatment-plan creation controls from the normal claimed appointment detail.
6. Add a dedicated consultant chat screen and route chat inbox rows to it.
7. Add a customer profile/history screen reachable from the consultant chat header.
8. Verify single-session packages still create only standard appointments.
9. Roll back by routing chat rows to the existing appointment detail and treating `unscheduled` sessions as the old empty-schedule scheduled sessions.

## Open Questions

- Should chat thread ids be normalized to treatment plan ids for all new package bookings, or should they continue using the first appointment id for lower migration risk?
- Should admin have a special override to create a custom treatment plan for exceptional cases, or should all customer-facing treatment journeys require an admin-created package first?
