## 1. Data Models and Constants

- [x] 1.1 Extend spa package model and Firestore mapping with `packageType`, `sessionCount`, `durationPerSessionMinutes`, `suggestedIntervalDays`, `requiresProgressPhotos`, `photoPolicy`, and `photoGuide`.
- [x] 1.2 Add appointment status constants and display metadata for `assigned`, `no_show`, and `rescheduled` while preserving existing statuses.
- [x] 1.3 Add treatment plan model and Firestore mapping helpers with customer, consultant, copied template, session count, status, note, and timestamp fields.
- [x] 1.4 Add treatment session model and Firestore mapping helpers with treatment plan, session number, schedule, status, appointment link, no-show, cancellation, reschedule, and completion metadata.
- [x] 1.5 Add chat thread and chat message models with participant, appointment, treatment plan, message, sender, and timestamp fields.
- [x] 1.6 Add progress photo model and Firestore mapping helpers with treatment plan, session, user, consultant, photo type, angle, image URL, note, uploader, and timestamp fields.

## 2. Admin Spa Package Configuration

- [x] 2.1 Update admin spa package form to choose package type: single-session service or treatment template.
- [x] 2.2 Show treatment-template fields only when package type requires them: session count, per-session duration, total price, and suggested interval.
- [x] 2.3 Add progress photo controls to the admin package form: requires photos, photo policy, and photo guide.
- [x] 2.4 Update admin spa package cards/details to display whether a package is single-session or a treatment template.
- [x] 2.5 Preserve compatibility for existing spa packages by defaulting missing package type to single-session and missing photo policy to no photos.

## 3. Appointment and Consultant Assignment Flow

- [x] 3.1 Update booking creation so treatment-template packages still create only the initial appointment request.
- [x] 3.2 Update consultant claim flow to set `status = assigned` and write consultant metadata through a transaction.
- [x] 3.3 Create or expose a chat thread after an appointment is assigned to a consultant.
- [x] 3.4 Update customer and admin appointment status displays to include assigned, no-show, and rescheduled.
- [x] 3.5 Add no-show action for assigned or confirmed appointments in consultant workflow.
- [x] 3.6 Ensure no-show does not increment completed treatment session count in MVP.

## 4. Consultant Treatment Workspace

- [x] 4.1 Add consultant appointment detail screen or detail section with customer info, appointment schedule, chat entry, consultation notes, and treatment actions.
- [x] 4.2 Add consultation note editing for assigned consultants and persist customer-visible notes.
- [x] 4.3 Let consultants create a treatment plan from the claimed package/template.
- [x] 4.4 Let consultants create a custom treatment plan when the claimed package is not a treatment template.
- [x] 4.5 Generate or represent treatment sessions from the treatment plan session count.
- [x] 4.6 Add consultant UI for treatment session timeline and per-session actions.
- [x] 4.7 Let consultants mark sessions completed, cancelled, no-show, or rescheduled with metadata.
- [x] 4.8 Add validation so consultants can only manage appointments and treatment plans assigned to themselves unless admin reassignment is used.

## 5. Consultation Chat

- [x] 5.1 Add chat thread creation or lookup for assigned appointments.
- [x] 5.2 Add customer chat UI under appointment or treatment plan detail.
- [x] 5.3 Add consultant chat UI under assigned appointment or treatment plan detail.
- [x] 5.4 Persist chat messages to Firestore and display them in chronological order.
- [x] 5.5 Prevent chat composer access for pending appointments without an assigned consultant.
- [x] 5.6 Link existing appointment chat thread to a treatment plan when the plan is created.

## 6. Progress Photos

- [x] 6.1 Add progress photo upload UI to consultant treatment session detail when package policy requires photos.
- [x] 6.2 Reuse existing Cloudinary upload helper for progress photo image uploads.
- [x] 6.3 Store uploaded before-session and after-session photo records with treatment/session context.
- [x] 6.4 Add optional photo note and angle/type metadata in the upload flow.
- [x] 6.5 Warn consultants when completing a session that requires photos but required photos are missing.
- [x] 6.6 Allow consultants to complete without required photos only after entering a skip reason.
- [x] 6.7 Do not require progress photos for no-show or cancelled sessions.
- [x] 6.8 Add customer progress photo viewing grouped by treatment session.

## 7. Customer Treatment Experience

- [x] 7.1 Add treatment plan list to the customer spa area.
- [x] 7.2 Add treatment plan detail showing plan summary, consultant, consultation note, and session timeline.
- [x] 7.3 Add session detail showing schedule, status, progress photos, and notes.
- [x] 7.4 Show no-show sessions clearly and allow customers to continue chat for rescheduling.
- [x] 7.5 Keep MVP treatment plans view-only for customers without an accept/reject action.

## 8. Admin Treatment Oversight

- [x] 8.1 Add admin treatment management entry point under the Spa admin group.
- [x] 8.2 Add treatment plan list with search and filters by status, customer, consultant, and package/template.
- [x] 8.3 Add treatment session filters for scheduled, completed, no-show, cancelled, and rescheduled sessions.
- [x] 8.4 Let admins reassign consultant ownership for treatment plans or active appointments.
- [x] 8.5 Let admins view progress photo records and hide or remove incorrect/sensitive photos.
- [x] 8.6 Add admin dashboard counts for active treatment plans and no-show sessions if space allows.

## 9. Access Control and Safety

- [x] 9.1 Add app-side guards so customers only see their own treatment plans, sessions, chat, and photos.
- [x] 9.2 Add app-side guards so consultants only manage assigned appointments, treatment plans, chat, and progress photos.
- [x] 9.3 Add app-side guards so admins can supervise all treatment records.
- [x] 9.4 Document Firestore rule requirements for treatment plans, sessions, chat, and progress photos.
- [x] 9.5 Handle missing optional fields gracefully for existing Firestore records.

## 10. Verification

- [x] 10.1 Verify existing single-session spa package browsing and booking still works.
- [x] 10.2 Verify treatment-template package booking creates only one initial pending appointment.
- [x] 10.3 Verify consultant claim opens chat and allows treatment plan creation.
- [x] 10.4 Verify customers can view treatment plan, sessions, chat, and progress photos.
- [x] 10.5 Verify no-show status is visible to customer, consultant, and admin and does not count as completed.
- [x] 10.6 Verify progress photos are required only for configured packages and can be skipped with a reason.
- [x] 10.7 Verify admin can view/reassign treatment ownership and review no-shows/photos.
- [x] 10.8 Run `.\gradlew.bat :app:assembleDebug` and fix any build errors.
