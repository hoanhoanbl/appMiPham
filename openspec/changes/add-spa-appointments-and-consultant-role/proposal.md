## Why

Customers can now browse spa packages, but they still cannot request an appointment for a package. The app also needs a dedicated consultant role so staff can review pending spa requests and claim them without using the admin dashboard.

## What Changes

- Add a Firestore-backed spa appointment flow that lets signed-in customers book an active spa package for a selected date and time slot.
- Add customer-facing appointment status visibility so customers can review their spa appointment requests and see consultant confirmation.
- Add a consultant role (`role = 2`) with routing to a dedicated consultant dashboard after login.
- Let consultants see all pending spa appointments and confirm one by assigning themselves as the appointment consultant.
- Use transaction-based confirmation so only one consultant can claim a pending appointment.
- Add admin visibility for spa appointment volume/status so admins can supervise appointments separately from product orders.
- Keep spa appointment payment, automated notifications, and full staff availability scheduling out of scope for this change.

## Capabilities

### New Capabilities
- `spa-appointments`: Customer booking, appointment storage, appointment status tracking, consultant confirmation, and admin appointment oversight.
- `consultant-role`: Role-based login routing and consultant-only workspace access.

### Modified Capabilities

## Impact

- Firestore: add a new `appointments` collection and read/write flows for appointment records.
- Android auth/navigation: update role routing to support `0 = customer`, `1 = admin`, and `2 = consultant`.
- Android UI: enable booking from spa package details, add customer appointment views, add a consultant dashboard, and add admin appointment management/status entry points.
- Android models: add spa appointment data model and Firestore mapping helpers.
- Concurrency: use Firestore transactions for consultant confirmation.
- Security/rules: Firebase rules should be updated outside the app if the project maintains rules separately.
