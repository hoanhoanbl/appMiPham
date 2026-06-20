## ADDED Requirements

### Requirement: Customers can book active spa packages
The system SHALL allow a signed-in customer to create a spa appointment request from an active spa package detail screen.

#### Scenario: Signed-in customer opens booking
- **WHEN** a signed-in customer views an active spa package detail screen and taps the booking action
- **THEN** the system opens a spa appointment booking form for that package

#### Scenario: Guest attempts booking
- **WHEN** a guest views an active spa package detail screen and taps the booking action
- **THEN** the system routes the user to login before appointment creation is allowed

#### Scenario: Inactive package cannot be booked
- **WHEN** the selected spa package is missing or inactive
- **THEN** the system does not create an appointment and shows that the package is unavailable

### Requirement: Booking form captures appointment details
The system SHALL collect appointment date, time slot, phone number, and optional customer note before creating a spa appointment.

#### Scenario: Missing required booking data
- **WHEN** the customer submits the booking form without a date, time slot, or phone number
- **THEN** the system prevents submission and shows validation feedback

#### Scenario: Valid booking data submitted
- **WHEN** the customer submits a booking form with a date, time slot, phone number, and optional note
- **THEN** the system creates an appointment request with `status = "pending"`

### Requirement: Appointment records preserve package and customer context
The system SHALL store appointment records in `appointments` with customer identity, customer contact, copied spa package details, appointment time, status, consultant assignment fields, and timestamps.

#### Scenario: Appointment document is created
- **WHEN** a valid booking is submitted for a spa package
- **THEN** the appointment document includes `userId`, customer email/name when available, phone number, `spaPackageId`, copied package name, copied package price, copied duration, `startAt`, `endAt`, `status`, `createdAt`, and `updatedAt`

#### Scenario: Package changes after booking
- **WHEN** an admin edits a spa package after an appointment was created
- **THEN** the appointment continues to display the copied package details stored on the appointment record

### Requirement: Appointment slots are constrained
The system SHALL offer fixed appointment time slots and SHALL prevent creation of obviously conflicting active appointments for the same selected slot according to the initial app-side conflict rules.

#### Scenario: Customer selects a fixed slot
- **WHEN** the customer chooses a date on the booking form
- **THEN** the system shows supported fixed time slots rather than accepting arbitrary time text

#### Scenario: Slot already has an active appointment
- **WHEN** the customer submits an appointment for a slot that already has a pending or confirmed conflicting appointment
- **THEN** the system prevents duplicate creation and asks the customer to choose another slot

### Requirement: Customers can view their spa appointments
The system SHALL provide a customer-facing view of appointments belonging to the signed-in user.

#### Scenario: Customer views appointment history
- **WHEN** a signed-in customer opens the spa appointment history view
- **THEN** the system lists appointments where `userId` matches the current Firebase Auth user id

#### Scenario: Confirmed appointment shows consultant
- **WHEN** a customer views an appointment with `status = "confirmed"` and consultant assignment fields
- **THEN** the system shows the confirmed status and consultant information when available

### Requirement: Customers can cancel eligible appointments
The system SHALL allow customers to cancel their own pending spa appointments.

#### Scenario: Customer cancels pending appointment
- **WHEN** the owner of a pending appointment requests cancellation
- **THEN** the system updates the appointment to `status = "cancelled"` and records cancellation metadata

#### Scenario: Customer cannot cancel someone else's appointment
- **WHEN** a customer attempts to cancel an appointment whose `userId` does not match the current user
- **THEN** the system does not update the appointment

### Requirement: Consultants can view pending appointments
The system SHALL provide consultants a dashboard view containing all spa appointments whose status is `pending`.

#### Scenario: Consultant opens pending list
- **WHEN** a signed-in consultant opens the consultant dashboard
- **THEN** the system lists all appointments with `status = "pending"`

#### Scenario: Pending list updates
- **WHEN** a pending appointment is created or leaves pending status
- **THEN** the consultant pending list reflects the change through Firestore updates

### Requirement: Consultants can confirm pending appointments safely
The system SHALL confirm appointments through a Firestore transaction that assigns the current consultant only when the appointment is still pending and unassigned.

#### Scenario: Consultant confirms available appointment
- **WHEN** a consultant confirms an appointment whose `status` is `pending` and `consultantId` is blank
- **THEN** the transaction sets `status = "confirmed"`, writes the consultant id/name/email, writes `confirmedAt`, and updates `updatedAt`

#### Scenario: Appointment already claimed
- **WHEN** a consultant confirms an appointment that is no longer pending or already has a consultant id
- **THEN** the transaction does not overwrite the appointment and the system shows that the appointment was already taken or changed

### Requirement: Consultants can view assigned appointments
The system SHALL provide consultants a view of appointments assigned to the current consultant.

#### Scenario: Consultant views assigned appointments
- **WHEN** a consultant opens the assigned appointments view
- **THEN** the system lists appointments where `consultantId` matches the current Firebase Auth user id

#### Scenario: Consultant completes appointment
- **WHEN** a consultant marks an assigned confirmed appointment as completed
- **THEN** the system updates the appointment to `status = "completed"` with completion metadata

### Requirement: Admins can supervise spa appointments
The system SHALL provide admins with appointment status counts and a management view for all spa appointments.

#### Scenario: Admin views appointment counts
- **WHEN** an admin opens the admin dashboard
- **THEN** the system shows counts for pending and active spa appointments

#### Scenario: Admin opens appointment management
- **WHEN** an admin opens spa appointment management
- **THEN** the system lists appointments across statuses and allows status-based filtering

#### Scenario: Admin cancels appointment
- **WHEN** an admin cancels a spa appointment
- **THEN** the system updates the appointment to `status = "cancelled"` and records cancellation metadata
