## 1. Shared UI Foundations

- [x] 1.1 Review touched consultant, admin, auth, and theme files for reusable local colors, spacing, status chips, section headers, and empty-state patterns.
- [x] 1.2 Add or refine small shared Compose helpers only where they reduce repeated UI code across the touched screens.
- [x] 1.3 Normalize visible Vietnamese copy touched by this change and remove encoding artifacts from updated strings.
- [x] 1.4 Keep existing Firestore write/read functions and role routing behavior intact while moving UI affordances.

## 2. Consultant Dashboard Polish

- [x] 2.1 Rename and restyle consultant bottom navigation and tab copy so schedule operations and messages are clearly distinct.
- [x] 2.2 Polish consultant header, schedule filters, loading states, and empty states with consistent operational hierarchy.
- [x] 2.3 Redesign consultant appointment cards to show customer, package, time, duration, status, and next valid action clearly.
- [x] 2.4 Replace "detail / chat / treatment" wording with operational appointment-detail copy.
- [x] 2.5 Polish the consultant messages inbox with customer identity, last message, package or treatment context, last activity, and a useful empty state.

## 3. Consultant Appointment Detail Workspace

- [x] 3.1 Remove embedded chat message listeners, chat message list, message composer state, and inline send action from consultant appointment detail.
- [x] 3.2 Add a clear open-conversation action in appointment detail when a chat thread exists for the appointment or linked treatment plan.
- [x] 3.3 Reorganize appointment detail into operational sections for customer summary, appointment status, notes, treatment plan summary, sessions, and progress photos.
- [x] 3.4 Polish consultation note and recommendation note controls with clear disabled states when the consultant cannot manage the appointment.
- [x] 3.5 Preserve existing appointment lifecycle, treatment session update, photo upload, and permission gates while changing layout.
- [x] 3.6 Verify terminal appointment statuses hide normal operational actions and show final-state context.

## 4. Consultant Chat Screen Polish

- [x] 4.1 Polish the dedicated consultant chat header with customer identity, profile affordance, and compact conversation context.
- [x] 4.2 Improve message bubbles, message list spacing, empty conversation state, access-denied state, and loading state.
- [x] 4.3 Improve the composer layout for stable mobile sizing, disabled state, send action, and readable placeholder copy.
- [x] 4.4 Ensure the chat screen remains message-only and does not show appointment actions, treatment cards, progress photo controls, or note forms.
- [x] 4.5 Verify tapping the customer header opens the customer profile/history screen with the existing thread/user extras.

## 5. Admin Appointment Management Polish

- [x] 5.1 Add compact summary metrics for appointment totals and important status buckets.
- [x] 5.2 Polish status filters, date filters, and search so they are easy to scan and do not crowd the list.
- [x] 5.3 Redesign admin appointment cards or rows to show package, customer, time, duration, status, consultant, customer note, and internal note clearly.
- [x] 5.4 Polish reassignment and cancellation dialogs with focused copy, destructive styling for cancellation, and preserved existing update behavior.
- [x] 5.5 Add polished empty and loading states for appointment management.

## 6. Admin Treatment Management Polish

- [x] 6.1 Polish plan search, status filters, and plan list cards with progress, customer, consultant, and status context.
- [x] 6.2 Improve selected-plan detail so progress summary, consultant assignment, chat or appointment context, and status are visible together.
- [x] 6.3 Present treatment sessions as a clear timeline/list with session number, schedule state, status, and date/time.
- [x] 6.4 Polish progress photo rows with thumbnail, type, uploader, note preview, and hide action.
- [x] 6.5 Add polished empty states for no selected plan, no sessions, and no progress photos.

## 7. Login Screen Polish

- [x] 7.1 Refresh the login screen visual hierarchy with spa-aligned brand presentation, form spacing, and touch-friendly controls.
- [x] 7.2 Preserve existing Firebase Auth login, role lookup timeout behavior, booking redirect, and role-based navigation.
- [x] 7.3 Polish login loading, error, home continuation, and register entry states.
- [x] 7.4 Ensure all visible login copy is readable Vietnamese without encoding artifacts.

## 8. Verification

- [x] 8.1 Build the Android app with `.\gradlew.bat :app:assembleDebug --console=plain`.
- [ ] 8.2 Manually verify consultant schedule tab opens operational appointment detail without an embedded chat composer.
- [ ] 8.3 Manually verify consultant messages tab opens the dedicated chat screen and sending still updates the thread last-message metadata.
- [ ] 8.4 Manually verify customer profile/history still opens from the chat header.
- [ ] 8.5 Manually verify consultant appointment lifecycle actions, treatment session actions, and progress photo upload still work from operational screens.
- [ ] 8.6 Manually verify admin appointment filtering, reassignment, and cancellation remain functional.
- [ ] 8.7 Manually verify admin treatment plan selection, session review, photo review, and reassignment remain functional.
- [ ] 8.8 Manually verify login routes customers, admins, consultants, and spa booking redirects to the same destinations as before.
