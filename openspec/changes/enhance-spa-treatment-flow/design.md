## Context

The app is a Kotlin Jetpack Compose Android app using Firebase Auth and Firestore directly from screens. Existing spa work already introduces `spa_packages`, `appointments`, a consultant role (`role = 2`), consultant confirmation, customer appointment history, and admin appointment oversight. That foundation models spa as one-off appointments.

The agreed next step is to make spa behavior closer to real operations: consultants claim appointments, chat with customers, record consultation notes, create customer-specific treatment plans from admin-managed templates, track each treatment session, handle no-shows, and optionally document visual progress through uploaded photos. The app already uses Cloudinary for product and spa package images, so progress photos can reuse that upload pattern unless a later change introduces another storage service.

## Goals / Non-Goals

**Goals:**
- Let admins configure spa packages as either single-session services or treatment templates.
- Let consultants create customer-specific treatment plans after claiming an appointment.
- Track multiple sessions within a treatment plan, including no-show, cancelled, completed, and rescheduled outcomes.
- Open chat only after a consultant claims an appointment.
- Let consultants record consultation notes and treatment recommendations.
- Let customers view treatment plans, session timelines, chat, and progress photos.
- Let consultants upload progress photos only for packages/treatments that require them.
- Let admins supervise treatment plans, sessions, consultant assignment, no-shows, and progress photo records.
- Keep treatment plan approval lightweight for MVP: consultant-created plans are visible to customers without a customer approval button.

**Non-Goals:**
- Do not implement deposits, payment, prepaid session accounting, or automatic session deduction.
- Do not implement automated reminders, push notifications, SMS, or email.
- Do not require customer approval before a treatment plan becomes visible.
- Do not let customers upload progress photos in MVP; consultant/staff upload is the primary source.
- Do not build a full staff availability calendar or scheduling engine.
- Do not enforce production security only in app code; Firestore rules must still be deployed separately.

## Decisions

### Model spa packages as service or treatment template

Add package-level fields such as `packageType`, `sessionCount`, `durationPerSessionMinutes`, `suggestedIntervalDays`, `requiresProgressPhotos`, `photoPolicy`, and `photoGuide`. A hair washing service can remain a single-session package, while an acne treatment can be a multi-session treatment template.

Alternative considered: create a separate `treatment_templates` collection. That keeps templates cleaner but splits the customer catalog and admin package management. Keeping templates in `spa_packages` is simpler for the current app because customers already browse spa packages.

### Keep appointments and treatment sessions distinct

Appointments remain the entry point from customer booking. Treatment sessions represent each planned visit in a treatment plan. A session can reference an appointment when it corresponds to a scheduled visit, but not every future session needs to become an appointment immediately.

Alternative considered: make every treatment session an appointment immediately. That gives uniform scheduling but can create too many future appointments before the customer and consultant have confirmed dates.

### Add treatment plans as customer-specific records

Use a `treatment_plans` collection for individual plans created by consultants. A plan copies key template/package details and stores customer, consultant, plan status, session counts, recommendation notes, and timestamps. This preserves plan history even if the admin later edits the package template.

Alternative considered: store a plan inside the appointment document. That is easier for a single appointment but becomes cramped once chat, sessions, photos, and admin oversight are added.

### Use session-level status for no-show and progress tracking

Session statuses should include `scheduled`, `completed`, `cancelled`, `no_show`, and `rescheduled`. Appointment statuses should expand to `pending`, `assigned`, `confirmed`, `completed`, `cancelled`, `no_show`, and `rescheduled`.

No-show does not deduct a treatment session in MVP because the app has no payment, deposit, or prepaid-session accounting yet. Consultants can chat with the customer and schedule another session.

Alternative considered: count no-show as a consumed session. That is operationally useful after payments/deposits exist but would be too strict for the current MVP.

### Open chat after consultant assignment

Create a chat thread when an appointment is claimed or when the detail screen first needs chat. Chat is tied to the claimed appointment and can optionally link to the treatment plan once created. Customers and the assigned consultant can exchange messages. Admins can supervise metadata and optionally view threads if required later.

Alternative considered: allow customers to chat before assignment. That would require a shared inbox or admin routing queue, which is outside the current consultant ownership model.

### Consultant uploads progress photos

Progress photos are uploaded by consultants/staff per treatment session. Use a separate `treatment_progress_photos` collection with fields for treatment plan, session, customer, consultant, photo type, angle, image URL, note, uploader, and timestamps. This supports multiple angles and independent deletion/moderation.

Alternative considered: store `beforePhotoUrls` and `afterPhotoUrls` arrays directly on each session. That is simple but less flexible for metadata, moderation, and future image management.

### Allow required photo skip with reason

If a package requires progress photos and the consultant attempts to complete a session without required photos, the UI should warn them. For MVP, the consultant can still complete the session only after entering a skip reason such as customer declined photos.

Alternative considered: block completion until photos exist. That can fail real visits where customers do not consent to photos.

### Keep customer plan approval out of MVP

Consultant-created treatment plans become visible to customers without an explicit approval action. This matches the user's chosen MVP direction and avoids adding approval state, rejection flows, and plan renegotiation UI before the core workflow is stable.

Alternative considered: require customers to accept plans. This is more formal but adds workflow complexity that can wait.

## Risks / Trade-offs

- App-side permissions are not security boundaries -> Add app guards for UX, but document Firestore rule requirements for customer, consultant, and admin access.
- Multi-session plans can outgrow simple fixed slots -> Start with session records and manual scheduling, then add availability management later.
- Progress photos are sensitive personal data -> Restrict upload/view access by role, store consent/skip reasons, and avoid showing photos outside customer/consultant/admin contexts.
- Chat can create notification expectations -> MVP uses in-app Firestore listeners only; automated notifications remain out of scope.
- No-show without session deduction can be abused -> Track no-show count and surface it to consultants/admins; add deposit/session deduction later if needed.
- Reusing Cloudinary keeps implementation familiar but exposes external media URLs -> Use existing upload/delete helpers and keep moderation/deletion available for admins where practical.
- Existing active changes already modify spa packages and appointments -> Implement this enhancement after or alongside those changes carefully to avoid merge conflicts.

## Migration Plan

1. Extend spa package model and admin form with treatment-template and progress-photo policy fields.
2. Add treatment plan, treatment session, chat thread/message, and progress photo models/mappers.
3. Extend appointment statuses and update all role-specific status labels/actions.
4. Add consultant appointment detail with chat, consultation notes, treatment plan creation, session management, no-show handling, and progress photo upload.
5. Add customer treatment plan detail with chat, session timeline, and progress photos.
6. Add admin treatment oversight and consultant reassignment controls.
7. Verify existing single-session booking still works and does not require treatment fields.
8. Roll back by hiding treatment/chat/progress entry points; existing `spa_packages` and `appointments` records remain readable with default values.

## Open Questions

- Should admins be able to read full chat message content in MVP, or only see thread metadata and intervene through appointment reassignment?
- Should treatment sessions be scheduled one at a time by consultants, or should creating a plan optionally pre-generate suggested session dates?
- Should progress photo consent be recorded as a simple skip reason, or should the booking/treatment flow include explicit customer consent text before photos are uploaded?
