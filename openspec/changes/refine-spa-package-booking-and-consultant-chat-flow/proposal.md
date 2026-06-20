## Why

The current spa flow still treats multi-session treatment plans as something a consultant creates after claiming an appointment. That does not match the desired business model: admins should sell complete spa packages with a fixed session count up front, while consultants operate the booked package and communicate with customers.

The consultant chat experience is also too mixed today because opening a chat conversation takes the consultant into an appointment detail screen that includes notes, treatment data, and session controls. Chat should feel like a dedicated customer conversation, with customer details and history available through an intentional profile entry point.

## What Changes

- Create treatment plans and treatment sessions automatically when a customer books a multi-session spa package.
- Preserve admin-created package structure as the source of truth for package name, price, session count, duration per session, suggested interval, and progress-photo policy.
- Remove the normal consultant flow for creating custom treatment plans or changing session count after claim.
- Add an `unscheduled` treatment session state for future sessions that exist in the plan but do not yet have a date/time.
- Keep appointments and treatment sessions distinct: the first booked visit creates an appointment and links to session 1; later sessions become appointments only when the customer chooses a concrete date/time.
- Update consultant claim behavior so accepting the first appointment assigns the appointment, treatment plan, treatment sessions, and chat thread to that consultant.
- Split consultant UI into dedicated work areas:
  - Schedule/appointment detail for operational actions.
  - Pure chat screen for messages only.
  - Customer profile/history screen opened from the chat header or customer name.
- Update customer package and treatment UI so customers clearly see the package image, fixed session count, progress, future sessions, and scheduling actions.
- Keep payments, deposits, automated reminders, push notifications, and prepaid-session deduction out of scope.

## Capabilities

### New Capabilities
- `spa-package-treatment-booking`: Admin-owned complete spa packages, booking-time treatment plan/session creation, customer session scheduling, and package-detail disclosure.
- `consultant-chat-workspace`: Consultant chat inbox, pure chat screen, customer profile/history entry point, and separation from appointment/treatment operations.

### Modified Capabilities

## Impact

- Firestore: booking multi-session packages must create or update `appointments`, `treatment_plans`, `treatment_sessions`, and later `consultation_chat_threads`.
- Android models: treatment session status metadata must support `unscheduled`; plan status should support waiting for consultant assignment.
- Android customer UI: spa details, booking confirmation, treatment plan detail, and session scheduling copy must reflect complete admin packages.
- Android consultant UI: dashboard chat tab should navigate to a dedicated chat screen instead of appointment detail; appointment detail should stop offering treatment plan creation in the normal package flow.
- Android admin UI: spa package form/cards should emphasize fixed package session count and treatment-package completeness.
- Security/rules: Firestore rules should continue enforcing customer, assigned consultant, and admin access for treatment plans, sessions, appointments, chat threads, and progress photos.
