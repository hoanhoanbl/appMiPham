## Why

The consultant workspace currently exposes the same customer conversation in both the chat inbox and the appointment detail screen, which makes the consultation flow feel duplicated and confusing. The schedule, treatment journey, admin oversight, and login surfaces also use uneven layouts and copy, so the spa workflow needs a focused UI polish pass that separates responsibilities and makes each role screen feel intentional.

## What Changes

- Redesign the consultant dashboard navigation and screen copy so `Schedule` is for operational appointment work and `Messages` is for conversations.
- Remove the embedded chat composer from consultant appointment detail and replace it with an explicit action that opens the dedicated message-only chat screen when appropriate.
- Polish the consultant chat inbox and chat screen with clearer customer context, last-message states, empty states, and access to customer profile/history from the header.
- Refine consultant appointment detail into an operational workspace for customer info, consultation notes, appointment status, treatment sessions, and progress photos.
- Improve admin appointment management with stronger status/date filtering, summary metrics, scannable appointment cards, and clearer reassignment/cancellation actions.
- Improve admin treatment management with a clearer master-detail layout, progress summaries, session timeline states, and photo context.
- Refresh the login screen to feel aligned with the spa brand while preserving existing role-based navigation behavior.
- Keep existing Firebase collections, role routing, appointment lifecycle, treatment records, and chat data contracts intact.

## Capabilities

### New Capabilities
- `consultant-workspace-ui`: Covers the polished consultant dashboard, schedule/chat separation, appointment detail workspace, and dedicated chat presentation.
- `spa-management-ui`: Covers polished admin appointment and treatment management surfaces for schedule oversight and treatment tracking.
- `auth-entry-ui`: Covers the refreshed login surface and role-aware entry experience.

### Modified Capabilities
- None.

## Impact

- Affected UI code:
  - `app/src/main/java/com/example/appbanmypham/ui/consultant/ConsultantDashboardActivity.kt`
  - `app/src/main/java/com/example/appbanmypham/ui/consultant/ConsultantAppointmentDetailActivity.kt`
  - `app/src/main/java/com/example/appbanmypham/ui/consultant/ConsultantChatActivity.kt`
  - `app/src/main/java/com/example/appbanmypham/ui/consultant/ConsultantCustomerProfileActivity.kt`
  - `app/src/main/java/com/example/appbanmypham/ui/admin/ManageSpaAppointmentActivity.kt`
  - `app/src/main/java/com/example/appbanmypham/ui/admin/ManageTreatmentActivity.kt`
  - `app/src/main/java/com/example/appbanmypham/ui/auth/LoginActivity.kt`
  - `app/src/main/java/com/example/appbanmypham/ui/theme/*`
- Affected product areas: consultant dashboard, consultant chat, appointment detail, admin appointment management, admin treatment oversight, and login.
- No new backend service, Firestore collection, external dependency, or authentication model is required.
