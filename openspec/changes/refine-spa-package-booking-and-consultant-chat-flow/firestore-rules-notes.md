## Firestore Rules Notes

This implementation still uses app-side guards, but production Firestore rules must enforce the same access model.

- Customers may create their own pending appointment bookings.
- Customers may create treatment plans and treatment sessions only as part of their own treatment-package booking flow, with `userId == request.auth.uid` and no consultant assigned yet.
- Customers may read their own treatment plans, treatment sessions, chat threads/messages, appointments, and visible progress photos.
- Customers may schedule only their own unscheduled/no-show treatment sessions, and the resulting appointment must keep the same `userId` and linked treatment plan/session context.
- Consultants may claim pending appointments and then update only appointments, treatment plans, sessions, chat threads/messages, and progress photos where `consultantId == request.auth.uid`.
- Consultants may not create custom customer treatment plans in the normal package flow.
- Consultants may read customer profile/history data only through appointments, plans, threads, sessions, or progress photos assigned to that consultant.
- Admins may read and update all spa packages, appointments, treatment plans, treatment sessions, chat metadata, and progress photos.
- No-show session updates must not increment `completedSessionCount`.
- Future sessions with `status = "unscheduled"` must not be treated as active appointment slots until an appointment is created.
