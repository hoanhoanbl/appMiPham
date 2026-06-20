## Context

The app is a Kotlin Jetpack Compose Android app using Firebase Auth and Firestore directly from activities/screens. Customers can currently browse active spa packages from the `spa_packages` collection and open a spa package detail screen, but the booking CTA is intentionally disabled. Authentication stores user records in `users/{uid}` with `role = 0` for customers and `role = 1` for admins, and login currently routes admins to `DashboardActivity` and everyone else to `ProductActivity`.

This change turns the spa package catalog into a reservation entry point and introduces a consultant workspace. Customers create appointment requests, consultants claim pending requests, and admins supervise appointment status. Appointment booking is separate from ecommerce orders because spa services are scheduled reservations rather than shipped products.

## Goals / Non-Goals

**Goals:**
- Add a customer booking flow from an active spa package detail.
- Store appointment requests in a new Firestore `appointments` collection.
- Let customers view their own spa appointments and status updates.
- Add `role = 2` for consultants and route consultants to a dedicated dashboard.
- Let consultants view all pending spa appointments and confirm one by assigning themselves.
- Use Firestore transactions when consultants confirm appointments to prevent duplicate claims.
- Add admin appointment visibility and dashboard counts without mixing spa appointments into product order management.
- Follow the existing Compose, Firebase Auth, Firestore listener, and mint-theme UI patterns.

**Non-Goals:**
- Do not add online payment or checkout for spa appointments.
- Do not add automated push/email/SMS notifications.
- Do not implement a full staff availability calendar or per-consultant working schedule.
- Do not introduce a backend service or Cloud Functions dependency.
- Do not allow public/guest appointment creation; customers must sign in before booking.
- Do not migrate spa packages into products or product orders.

## Decisions

### Use a separate `appointments` collection

Appointments should be stored in a top-level `appointments` collection with fields copied from the selected spa package at booking time: package id, package name, price, and duration. Copying display-critical package fields preserves appointment history even if the admin later renames or edits the package.

Alternative considered: nested `users/{uid}/appointments`. That makes customer reads simple but makes consultant and admin global pending lists harder because Firestore collection group queries would be required and the app currently favors direct top-level collections.

### Use role value `2` for consultants

The existing role model is numeric and already uses `0 = customer` and `1 = admin`. Adding `2 = consultant` keeps the current data shape and only requires routing and UI permission updates.

Alternative considered: replace numeric roles with role strings. That reads better but would require a wider migration and risks breaking existing admin checks.

### Require sign-in before booking

The booking CTA should redirect unauthenticated users to login instead of creating anonymous appointment records. Appointments need a stable `userId`, contact metadata, and a customer appointment history.

Alternative considered: allow guest bookings with phone/email only. That is convenient but creates identity, cancellation, and status-tracking complexity outside the current app auth model.

### Store appointment time as millis plus display labels

Each appointment should store `startAt` and `endAt` as `Long` millis for sorting/filtering and optional `appointmentDateLabel` / `timeSlotLabel` strings for UI display. This matches the current app's simple timestamp style while avoiding string-only scheduling logic.

Alternative considered: store only date and time strings. That is easier to render but makes sorting, day filters, and conflict checks more fragile.

### Start with fixed booking slots

The first booking version should provide a small fixed set of time slots, such as `09:00`, `10:30`, `13:30`, `15:00`, and `16:30`, then calculate `endAt` from the package duration. This keeps the UI and validation predictable without building a full calendar system.

Alternative considered: free-form time input. It is fast to implement but creates invalid times, inconsistent formatting, and poor customer experience.

### Use transaction-based consultant confirmation

Consultant confirmation must use a Firestore transaction that reads the appointment document, verifies `status == "pending"` and `consultantId` is blank, then writes `status = "confirmed"` and the current consultant metadata. If either condition fails, the UI should show that another consultant has already taken the appointment.

Alternative considered: direct document update. That can allow two consultants to confirm the same pending appointment from stale list data.

### Keep appointment state simple

The first state machine should be:

```text
pending -> confirmed -> completed
pending -> cancelled
confirmed -> cancelled
```

`rescheduled` should be deferred because it requires slot history, conflict handling, and additional customer/consultant communication.

### Add admin oversight separately from consultant work

Admins should see all spa appointments and status counts, but the consultant workflow should remain a dedicated dashboard focused on pending and assigned appointments. This avoids overloading the ecommerce order management screen.

Alternative considered: let admins use the consultant dashboard. That is faster but blurs role responsibilities and makes later appointment reporting harder.

## Risks / Trade-offs

- Missing Firestore rules in the repo -> Document the required access model and keep app-side role checks consistent; deploy rules separately if the Firebase project stores rules outside this repository.
- Numeric roles can be unclear -> Centralize role constants where practical and update comments/copy to include consultant meaning.
- Fixed slots can still overbook without server-side constraints -> Check for conflicting active appointments before creation and rely on consultant/admin oversight for the initial version.
- Firestore queries with `whereEqualTo` and ordering may require indexes -> Start with simple listeners and client-side sorting where expected data volume is small; add indexes if Firebase reports them.
- App-side permissions are not security boundaries -> Firebase rules must enforce customer/consultant/admin access in production.
- Package snapshots can become stale -> Copy package metadata at booking time and use the copied fields for appointment cards/details.

## Migration Plan

1. Add appointment model/mapping and new activities/screens without changing existing product order data.
2. Enable the spa detail booking CTA for signed-in customers and route guests to login.
3. Add consultant routing for users whose `users/{uid}.role` is `2`.
4. Manually assign consultant role in Firestore for test accounts until a future user-management screen exists.
5. Add admin appointment entry points and dashboard appointment counts.
6. Roll back by hiding appointment entry points and ignoring the `appointments` collection; existing products, orders, and spa packages remain unaffected.

## Open Questions

- Should admin role assignment for consultants remain manual in Firebase Console for this change, or should a separate user-management change add role editing?
- Should customers be allowed to cancel confirmed appointments, or only pending appointments?
- Should appointment conflict checks be global per slot or scoped by spa package/category for the initial version?

## Firebase Rules / Index Notes

- This repository does not currently contain Firestore security rules, so production rule deployment must be handled in the Firebase project configuration outside this repo.
- Rules should allow customers to create appointments for themselves, read their own appointments, and cancel their own pending appointments only.
- Rules should allow consultants (`users/{uid}.role = 2`) to read pending appointments, read appointments assigned to themselves, and confirm/complete appointments only through valid status transitions.
- Rules should allow admins (`users/{uid}.role = 1`) to read and manage all appointments.
- The app uses direct collection queries on `appointments` for `userId`, `status`, `consultantId`, and `startAt`; Firebase may request single-field or composite indexes during runtime depending on the deployed rules/query combinations.
