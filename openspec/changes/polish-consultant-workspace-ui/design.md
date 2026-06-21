## Context

The app is a Kotlin Jetpack Compose Android app using Firebase Auth and Firestore directly from UI screens. Recent spa changes introduced consultant roles, pooled appointment capacity, appointment lifecycle states, treatment plans, treatment sessions, progress photos, consultant chat threads, and a dedicated consultant chat activity.

The current consultant experience still feels split incorrectly:

- The dashboard has separate schedule and chat tabs, but appointment detail still embeds a full "Chat with customer" card and composer.
- Schedule cards still advertise "detail / chat / treatment", which reinforces the idea that one screen owns every workflow.
- The chat inbox is technically dedicated, but it reads like a simple list rather than a workspace with useful conversation context.
- Admin appointment and treatment management screens work, but they are dense card lists with limited hierarchy and uneven visual polish.
- The login screen routes users correctly, but its presentation is not yet aligned with a premium spa operations/customer entry experience.

This change is a UI/UX polish pass. It should preserve existing data models, Firestore contracts, role routing, and lifecycle rules while making each surface clearer and more professional.

## Goals / Non-Goals

**Goals:**

- Make the consultant workspace read as two clean modes: schedule operations and customer messages.
- Remove the confusing duplicate chat composer from consultant appointment detail.
- Keep appointment and treatment actions available from operational screens, not from chat.
- Make consultant chat feel like a complete conversation workspace with useful customer and treatment context.
- Improve admin appointment management for scanning, filtering, reassignment, cancellation, and status oversight.
- Improve admin treatment management for plan progress, session timelines, photo context, and consultant assignment.
- Refresh login visual design while preserving the current role-based navigation and redirect behavior.
- Improve Vietnamese copy, empty states, spacing, hierarchy, and visual consistency across these screens.

**Non-Goals:**

- Do not add new Firestore collections or change document schemas.
- Do not alter appointment capacity reservation, appointment lifecycle permissions, or treatment session business rules.
- Do not add push notifications, unread counters backed by new server logic, or real-time typing indicators.
- Do not introduce a new design system dependency or image generation requirement.
- Do not redesign the customer shopping or booking flow beyond copy needed for consistency.

## Decisions

### Separate consultant workflows by intent

The consultant dashboard should keep bottom navigation for two high-level modes:

- `Schedule`: pending appointments, assigned appointments, lifecycle actions, appointment detail, consultation notes, treatment sessions, and progress photos.
- `Messages`: conversation inbox, dedicated chat, and intentional navigation to customer profile/history.

Appointment detail should no longer display an embedded chat transcript or composer. If a consultant needs to message the customer from appointment detail, show a compact contextual action such as "Open conversation" that launches `ConsultantChatActivity` with the existing thread id.

Alternative considered: keep a small recent-message preview in appointment detail. This still risks recreating the duplicate-chat mental model, so the default design should avoid message previews and use a single clear navigation action.

### Treat appointment detail as an operational console

Consultant appointment detail should be structured around the work done during a visit:

1. Customer and booking summary.
2. Current appointment status and allowed next actions.
3. Consultation note and recommendation fields.
4. Linked treatment plan summary.
5. Session list with schedule state, progress photo requirements, and valid operational actions.

This makes the screen useful during service without competing with the chat screen.

Alternative considered: split notes, sessions, and photos into separate tabs. That may be useful later, but for the current mobile scope a single scrollable operational detail with stronger sections is simpler and less disruptive.

### Make chat conversational, not administrative

The consultant chat inbox should show customer identity, last message, package/treatment context when available, and the last activity time. The chat screen should prioritize message readability, stable composer placement, and a compact header with customer profile access.

The chat screen should not show appointment status buttons, treatment session cards, progress photo controls, or note forms.

Alternative considered: add a side panel or collapsible customer context inside chat. On mobile this would crowd the conversation, so customer context should remain accessible through the header/profile route.

### Use a restrained premium spa operations visual direction

The aesthetic should be calm, premium, and operational:

- Backgrounds: warm near-white/pink only as page canvas, not dominant decoration.
- Primary accent: mint/teal for positive actions and active navigation.
- Secondary accents: amber for waiting/no-show, blue for scheduled/confirmed, red for destructive actions, muted violet only for reassignment/rescheduled states.
- Cards: flatter, tighter, and more information-dense than marketing cards.
- Shape: keep cards and controls around 8-16dp radius, avoid oversized pill-heavy layouts where they reduce scan speed.
- Typography: use existing Compose typography, but fix hierarchy through size, weight, line height, and copy rather than adding a dependency.

Alternative considered: a more decorative spa theme with large hero imagery and gradients. That fits customer marketing pages better than consultant/admin operations, where scanability matters more.

### Improve admin management as oversight dashboards

Admin appointment management should start with compact summary metrics and clear filters, then show scannable appointment rows/cards with the operational facts admins need: status, time, customer, package, consultant, capacity/internal notes, and available actions.

Admin treatment management should behave like a master-detail workspace: choose a treatment plan, then inspect progress, sessions, photos, and consultant assignment. It should avoid burying selected-plan detail beneath long unrelated lists.

Alternative considered: keep the current list-first screens and only adjust colors. That would not solve the usability problem of dense operational data lacking hierarchy.

### Refresh login without changing auth behavior

The login screen should keep Firebase Auth, role lookup, redirect to spa booking, and role-based destination routing unchanged. The UI polish should focus on brand consistency, clear form hierarchy, touch-friendly controls, error/loading states, and a calmer spa entry experience.

Alternative considered: add forgot-password functionality as part of the polish. That is a real feature and should be handled separately because it changes auth behavior and verification scope.

## Risks / Trade-offs

- Removing embedded chat from appointment detail may feel like losing convenience -> Add an explicit "Open conversation" action when a thread exists and ensure it is easy to find.
- UI polish may accidentally alter business logic while moving actions -> Keep lifecycle and Firestore update functions intact unless a small signature change is needed for navigation.
- Admin screens can become too visually busy with metrics and filters -> Keep summaries compact and prioritize only actionable operational states.
- Existing Vietnamese text contains encoding issues in some files -> Fix visible copy touched by this change and avoid broad unrelated rewrites.
- Dynamic Material colors may reduce brand consistency on Android 12+ -> Prefer explicit colors on these custom screens and consider disabling dynamic color in a separate theming change if needed.

## Migration Plan

1. Polish shared UI constants or small local helpers only where they reduce duplication across the touched screens.
2. Update consultant dashboard labels, filter copy, empty states, appointment card actions, and chat inbox presentation.
3. Remove embedded chat state/listeners/composer from consultant appointment detail and add an explicit open-chat action.
4. Refine consultant appointment detail layout around operational sections and valid lifecycle/treatment actions.
5. Refine consultant chat header, empty states, message bubbles, composer, and customer profile affordance.
6. Polish admin appointment management with summaries, filters, search, and clearer cards/actions.
7. Polish admin treatment management with selected-plan detail, session timeline states, and progress photo presentation.
8. Refresh login layout and copy while preserving auth and routing behavior.
9. Build and manually verify consultant/admin/login flows.

Rollback: restore appointment detail to the previous layout and keep chat navigation through the dashboard inbox. Because this change does not require data migration, rollback is limited to UI code.

## Open Questions

- Should the consultant appointment detail show a one-line "last message" timestamp next to the open-chat action, or should it avoid message metadata entirely?
- Should login copy keep the current customer-shop framing or shift to a combined customer/staff spa access message?
