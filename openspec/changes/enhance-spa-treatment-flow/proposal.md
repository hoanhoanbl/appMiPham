## Why

The current spa appointment flow supports basic one-off bookings, but it does not model the real spa workflow after a consultant accepts a customer: chat, consultation notes, multi-session treatment planning, missed visits, and progress tracking. This change upgrades spa appointments into a practical treatment journey for services such as acne extraction or skin recovery while keeping simple services like hair washing lightweight.

## What Changes

- Introduce treatment templates on spa packages so admins can define whether a package is a single-session service or a multi-session treatment template.
- Add consultant-owned treatment plans for individual customers, created after a consultant claims an appointment.
- Add treatment sessions so each visit in a plan can be scheduled, completed, cancelled, rescheduled, or marked as no-show.
- Add appointment statuses that better match the spa workflow: `pending`, `assigned`, `confirmed`, `completed`, `cancelled`, `no_show`, and `rescheduled`.
- Add customer-consultant chat that opens only after a consultant claims the appointment.
- Add consultation notes and treatment recommendations visible to the customer in MVP without requiring customer approval.
- Add optional progress photo tracking for treatment packages that require before/after or after-session images.
- Let consultants upload progress photos per treatment session and record reasons when required photos are skipped.
- Let customers view their treatment plan, sessions, chat, and progress photo timeline.
- Let admins supervise treatment plans, sessions, no-shows, consultant assignment, and progress photo records.
- Keep payment, deposits, automated reminders, push notifications, and customer approval of treatment plans out of scope for this change.

## Capabilities

### New Capabilities
- `spa-treatment-plans`: Multi-session spa treatment templates, customer-specific treatment plans, treatment sessions, session statuses, and role-specific treatment visibility.
- `spa-consultation-chat`: Customer-consultant chat and consultation notes tied to claimed appointments and treatment plans.
- `spa-progress-photos`: Optional before/after or after-session progress photo tracking for treatment sessions that require visual progress documentation.

### Modified Capabilities

## Impact

- Firestore: extend `spa_packages` with treatment-template and progress-photo policy fields; add treatment plan/session, chat thread/message, and progress photo collections.
- Android models: add treatment plan, treatment session, chat, and progress photo models/mappers.
- Android customer UI: enhance spa area with treatment plan detail, session timeline, chat, and progress photo viewing.
- Android consultant UI: enhance consultant dashboard with claimed appointment detail, chat, consultation notes, treatment plan creation, session management, no-show handling, and progress photo upload.
- Android admin UI: enhance admin oversight for treatment plans, sessions, consultant assignment, no-shows, and progress photo moderation.
- Existing appointment/package flows: booking continues to start from active spa packages, but treatment templates can produce multi-session plans after consultant handling.
- Storage/media: reuse the existing Cloudinary image upload pattern for progress photos unless a later implementation chooses another storage provider.
- Security/rules: Firebase rules must enforce customer, consultant, and admin access for chat, treatment plans, treatment sessions, and progress photos outside the app.
