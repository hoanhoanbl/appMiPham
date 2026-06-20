## 1. Appointment Model and Helpers

- [x] 1.1 Add a `SpaAppointment` model with Firestore-safe defaults for customer, package, schedule, status, consultant, and timestamp fields.
- [x] 1.2 Implement mapping helpers for reading `appointments` documents into `SpaAppointment` instances.
- [x] 1.3 Implement helper data/constants for appointment statuses, fixed booking slots, and status display metadata.
- [x] 1.4 Implement helper logic to calculate `startAt`, `endAt`, date labels, and time slot labels from the selected booking date and fixed slot.
- [x] 1.5 Implement app-side conflict checking for pending or confirmed appointments in the same selected slot before creating a new booking.

## 2. Consultant Role and Navigation

- [x] 2.1 Add role constants or clear role documentation for `0 = customer`, `1 = admin`, and `2 = consultant`.
- [x] 2.2 Update registration role comments/defaults so newly registered users remain customers with `role = 0`.
- [x] 2.3 Update login routing so `role = 2` opens the consultant dashboard, `role = 1` opens the admin dashboard, and customers open the product screen.
- [x] 2.4 Update already-authenticated session routing so existing consultant sessions open the consultant dashboard instead of the customer product screen.
- [x] 2.5 Add consultant dashboard activity registration to `AndroidManifest.xml`.

## 3. Customer Booking Flow

- [x] 3.1 Enable the spa package detail booking CTA and route guests to login before booking.
- [x] 3.2 Create a customer spa booking activity/screen that loads the selected active spa package.
- [x] 3.3 Build booking form UI for selected date, fixed time slot, phone number, and optional customer note.
- [x] 3.4 Validate booking form inputs and prevent submission for missing package, inactive package, missing date, missing slot, or missing phone number.
- [x] 3.5 Create `appointments` documents with copied package details, current customer id/email/name, phone number, schedule fields, `status = "pending"`, and timestamps.
- [x] 3.6 Show booking success and failure feedback, including unavailable/conflicting slot messages.
- [x] 3.7 Add customer appointment history UI that lists appointments where `userId` matches the current user.
- [x] 3.8 Show customer appointment status, schedule, copied package details, and consultant information when available.
- [x] 3.9 Allow customers to cancel their own pending appointments and record cancellation metadata.

## 4. Consultant Dashboard

- [x] 4.1 Create `ConsultantDashboardActivity` with Compose layout consistent with the app style.
- [x] 4.2 Guard consultant dashboard actions so non-consultant users cannot perform consultant-only actions.
- [x] 4.3 Add a pending appointments view that listens to appointments with `status = "pending"` and sorts them by appointment time or creation time.
- [x] 4.4 Add an assigned appointments view that lists appointments where `consultantId` matches the current user id.
- [x] 4.5 Implement transaction-based appointment confirmation that only succeeds when `status = "pending"` and `consultantId` is blank.
- [x] 4.6 Show a clear message when confirmation fails because another consultant already claimed or changed the appointment.
- [x] 4.7 Allow consultants to mark their own confirmed appointments as completed with completion metadata.
- [x] 4.8 Add consultant logout/back navigation behavior consistent with the admin/customer areas.

## 5. Admin Appointment Oversight

- [x] 5.1 Add appointment counts to the admin dashboard for pending and active spa appointments.
- [x] 5.2 Add an admin dashboard entry point for spa appointment management.
- [x] 5.3 Create an admin spa appointment management activity/screen listing appointments across statuses.
- [x] 5.4 Add status filters/search controls for admin appointment management.
- [x] 5.5 Show appointment customer, package, schedule, status, consultant, and note information in admin cards/details.
- [x] 5.6 Allow admins to cancel spa appointments and record cancellation metadata.
- [x] 5.7 Register any new admin appointment activities in `AndroidManifest.xml`.

## 6. Integration and Verification

- [ ] 6.1 Verify spa package browsing still works and the detail screen only books active packages.
- [ ] 6.2 Verify customer booking creates a pending appointment with copied package details and correct schedule fields.
- [ ] 6.3 Verify duplicate/conflicting slot creation is prevented by app-side validation.
- [ ] 6.4 Verify consultant login routes to the consultant dashboard for `role = 2`.
- [ ] 6.5 Verify consultants see all pending appointments and can claim one through the transaction flow.
- [ ] 6.6 Verify a second consultant cannot overwrite an appointment already confirmed by another consultant.
- [ ] 6.7 Verify customers see their own appointment statuses and cannot cancel another customer's appointment.
- [ ] 6.8 Verify admins can see appointment counts, list appointments, filter by status, and cancel appointments.
- [x] 6.9 Run `.\gradlew.bat :app:assembleDebug` and fix any build errors.
- [x] 6.10 Note Firebase rule/index requirements discovered during runtime testing for deployment outside this repo if needed.
