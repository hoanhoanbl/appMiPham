## Firestore Rule Requirements

The app now includes app-side guards for treatment plans, treatment sessions, consultation chat, and progress photos, but Firebase rules must enforce the same boundaries in production.

- Customers may read treatment plans, sessions, chat threads/messages, and progress photos only when `userId == request.auth.uid`.
- Customers may create chat messages only in threads where they are the thread customer and the thread has a consultant.
- Consultants may read and write assigned appointments, treatment plans, sessions, chat, and progress photos only when `consultantId == request.auth.uid`.
- Consultants may upload progress photos only for treatment sessions that belong to their assigned treatment plans.
- Admins may read and update all treatment records, including reassignment fields and photo moderation fields such as `isHidden`, `hiddenReason`, and `hiddenAt`.
- Pending appointments without `consultantId` must not expose an active chat composer to customers or consultants.
- No-show session updates must not increment `completedSessionCount`.
