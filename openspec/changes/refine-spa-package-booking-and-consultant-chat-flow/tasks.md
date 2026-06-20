## 1. Model and Status Updates

- [x] 1.1 Add `unscheduled` to treatment session status constants, labels, colors, and status helper metadata.
- [x] 1.2 Add a pre-assignment treatment plan status such as `waiting_consultant` and display metadata for customer/admin/consultant UI.
- [x] 1.3 Update treatment session readers so legacy sessions with no concrete schedule display and behave like `unscheduled`.
- [x] 1.4 Review treatment plan/session Firestore mapping defaults so booking-created records preserve package snapshots and tolerate old records.

## 2. Booking-Time Treatment Journey Creation

- [x] 2.1 Update customer spa package detail copy to describe complete admin-defined packages instead of consultant-created plans.
- [x] 2.2 Update booking confirmation copy for treatment packages to explain that the first visit is booked and the full session list is created immediately.
- [x] 2.3 Replace single appointment creation for treatment packages with an atomic booking operation that creates appointment, treatment plan, and session records.
- [x] 2.4 Link session 1 to the first appointment with concrete schedule data and mark later sessions as `unscheduled`.
- [x] 2.5 Preserve lightweight appointment-only creation for single-session spa packages.
- [x] 2.6 Add failure handling so partial booking records are not shown if the multi-document booking operation fails.

## 3. Consultant Claim and Appointment Operations

- [x] 3.1 Update consultant claim logic to find the booking-created treatment plan linked to the pending appointment.
- [x] 3.2 Assign consultant identity to appointment, treatment plan, and all linked treatment sessions during claim.
- [x] 3.3 Create or update the chat thread with customer, consultant, appointment, and treatment plan context during claim.
- [x] 3.4 Remove or hide consultant controls for creating custom treatment plans or changing session count in the normal claimed package flow.
- [x] 3.5 Keep consultant operational controls for completing, cancelling, rescheduling, marking no-show, and uploading progress photos on scheduled sessions.
- [x] 3.6 Ensure no-show updates do not increase completed session count and keep unfinished work visible.

## 4. Customer Treatment Scheduling UI

- [x] 4.1 Update customer treatment plan cards/details to show spa package image, fixed session count, progress, and package metadata.
- [x] 4.2 Display unscheduled future sessions clearly with a customer action to choose date/time.
- [x] 4.3 Update customer session scheduling so selecting a future session creates an appointment and updates that session to `scheduled`.
- [x] 4.4 Hide or disable occupied active appointment slots when customers schedule treatment sessions.
- [x] 4.5 Keep the existing chat entry available only after consultant assignment.

## 5. Consultant Chat Workspace

- [x] 5.1 Add a dedicated consultant chat screen that loads a chat thread, messages, and current consultant identity.
- [x] 5.2 Implement consultant message sending from the dedicated chat screen and update thread last-message metadata.
- [x] 5.3 Route consultant chat inbox rows to the dedicated chat screen instead of the combined appointment detail screen.
- [x] 5.4 Update chat inbox copy/icons so it communicates message conversations rather than appointment detail access.
- [x] 5.5 Ensure unassigned consultants cannot send messages in threads they do not own.
- [x] 5.6 Preserve compatibility for appointment-only chat threads that do not have a treatment plan id.

## 6. Consultant Customer Profile and History

- [x] 6.1 Add a consultant customer profile/history screen reachable from the chat header or customer name/avatar.
- [x] 6.2 Show customer identity information, phone/email, active treatment plans, appointment history, and no-show count.
- [x] 6.3 Show progress-photo context or links for treatment plans where progress photos exist.
- [x] 6.4 Keep appointment/session actions out of the customer profile screen unless they navigate back to operational detail screens.

## 7. Admin and Compatibility Review

- [x] 7.1 Review admin spa package cards/forms so multi-session packages read as complete sellable packages with fixed session count.
- [x] 7.2 Review admin treatment oversight so booking-created plans with `waiting_consultant`, `active`, completed, cancelled, and no-show data are readable.
- [x] 7.3 Document Firestore rule expectations for booking-created plans, sessions, chat threads, messages, and progress photos.
- [x] 7.4 Keep existing consultant-created legacy plans readable while preventing new normal-flow consultant-created plans.

## 8. Verification

- [x] 8.1 Build the Android app with `.\gradlew.bat :app:assembleDebug --console=plain`.
- [ ] 8.2 Manually verify single-session package booking still creates only one pending appointment.
- [ ] 8.3 Manually verify multi-session package booking creates appointment, treatment plan, and session records with session 1 scheduled and later sessions unscheduled.
- [ ] 8.4 Manually verify consultant claim assigns appointment, plan, sessions, and chat ownership.
- [ ] 8.5 Manually verify consultant chat tab opens only the dedicated message UI and customer profile opens separately.
- [ ] 8.6 Manually verify customer can schedule a later treatment session and occupied slots are unavailable.
