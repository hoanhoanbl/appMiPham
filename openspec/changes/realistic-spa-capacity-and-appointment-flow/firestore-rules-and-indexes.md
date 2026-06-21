## Firestore Rules Notes

### Capacity settings and overrides

- Only admins may create, update, or delete `spa_capacity_settings/{id}`.
- Only admins may create, update, or delete `spa_capacity_overrides/{dateKey}`.
- Customers and consultants may read effective capacity data needed to render availability, but they must not write capacity settings or overrides.

### Appointment creation and customer access

- Signed-in customers may create appointments only for their own `userId`.
- Customer-created appointments must start in `pending`, must not set consultant fields, terminal statuses, lifecycle timestamps, or reassignment fields.
- Customers may read their own appointments and treatment plans.
- Customers may cancel their own future `pending` appointments only through fields allowed for cancellation: `status=cancelled`, `cancelledAt`, `cancelReason`, and `updatedAt`.

### Consultant-owned updates

- Consultants may read appointments, treatment plans, sessions, progress photos, and chat threads assigned to their `consultantId`.
- Consultants may claim pending appointments only when the existing appointment has no consultant owner.
- Consultants may move assigned appointments through `checked_in`, `in_service`, `completed`, and `no_show` only when their auth uid matches the current `consultantId`.
- Consultants may update treatment-session progress fields only for sessions assigned to them and linked to their treatment plan.
- Consultants may send chat messages only into active threads where the thread `consultantId` equals their auth uid.

### Forgery prevention

- Customers and consultants must not directly write `appointment_capacity_blocks`.
- Only app-controlled booking, cancellation, and rescheduling flows should mutate capacity counters. If rules cannot identify trusted client flows, move capacity writes to Cloud Functions before production.
- Customers and consultants must not set or change `consultantId`, `consultantEmail`, `consultantName`, `assignedRoomName`, `assignedSpecialistName`, `internalStaffNote`, terminal statuses, `completedAt`, `noShowAt`, or reassignment fields unless the role-specific transition explicitly allows it.
- Admins may update operational fields, reassign consultants, and manage cancellation/rescheduling metadata.

### Chat ownership

- Customers may read/send messages in threads linked to their own appointment or treatment plan.
- Consultants may read/send messages only when the thread's current `consultantId` matches their auth uid.
- Previous consultants should retain historical readability only if the business intentionally allows audit access; they should not send new messages after reassignment.

## Index Notes

- `appointments`: `startAt` ascending, `status` ascending for availability and active appointment lookups.
- `appointments`: `userId` ascending, `createdAt` descending for customer appointment lists.
- `appointments`: `consultantId` ascending, `startAt` ascending for consultant schedules.
- `appointment_capacity_blocks`: `blockStartAt` ascending for booking horizon reads.
- `treatment_plans`: `userId` ascending, `createdAt` descending for customer plans.
- `treatment_sessions`: `treatmentPlanId` ascending, `sessionNumber` ascending for treatment timelines.
- `consultation_chat_threads`: `consultantId` ascending, `updatedAt` descending for consultant inbox.
- `consultation_chat_threads`: `treatmentPlanId` ascending for canonical treatment chat lookups.
