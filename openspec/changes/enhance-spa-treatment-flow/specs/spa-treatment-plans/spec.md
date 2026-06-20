## ADDED Requirements

### Requirement: Admins can configure spa packages as treatment templates
The system SHALL let admins classify spa packages as single-session services or multi-session treatment templates.

#### Scenario: Admin creates a single-session service
- **WHEN** an admin creates or edits a spa package with `packageType = "single_session"`
- **THEN** the package stores one service duration and price without requiring a session count

#### Scenario: Admin creates a treatment template
- **WHEN** an admin creates or edits a spa package with `packageType = "treatment_template"`
- **THEN** the package stores session count, per-session duration, total price, and suggested interval information

### Requirement: Customers can book the first appointment for a treatment template
The system SHALL allow customers to start from a treatment-template package while creating only an initial appointment request.

#### Scenario: Customer books a treatment template
- **WHEN** a signed-in customer books an active treatment-template package
- **THEN** the system creates an appointment request for the selected date and slot without creating all future sessions immediately

#### Scenario: Customer views treatment-template details
- **WHEN** a customer views a treatment-template package
- **THEN** the system shows the expected session count and treatment nature before booking

### Requirement: Consultants can create customer-specific treatment plans
The system SHALL let consultants create treatment plans for customers after claiming an appointment.

#### Scenario: Consultant creates a plan from a template
- **WHEN** a consultant has claimed an appointment and selects a treatment-template package
- **THEN** the system creates a treatment plan with copied package details, customer identity, consultant identity, session count, notes, and `status = "active"`

#### Scenario: Consultant creates a custom plan
- **WHEN** a consultant has claimed an appointment and enters custom treatment plan details
- **THEN** the system creates a customer-specific treatment plan not requiring customer approval in MVP

### Requirement: Treatment plans contain treatment sessions
The system SHALL track each planned visit as a treatment session under a treatment plan.

#### Scenario: Plan sessions are generated
- **WHEN** a treatment plan is created with a session count greater than zero
- **THEN** the system creates or represents session entries numbered from 1 through the session count

#### Scenario: Session references appointment when scheduled
- **WHEN** a treatment session is scheduled for a concrete date and time
- **THEN** the system records schedule fields and may reference the related appointment record

### Requirement: Consultants can manage treatment session outcomes
The system SHALL let consultants mark treatment sessions as completed, cancelled, no-show, or rescheduled.

#### Scenario: Consultant completes a session
- **WHEN** the assigned consultant completes a scheduled treatment session
- **THEN** the system marks the session as `completed`, records completion metadata, and updates the treatment plan completed-session count

#### Scenario: Consultant marks no-show
- **WHEN** the customer does not arrive for a scheduled treatment session
- **THEN** the consultant can mark the session and related appointment as `no_show` without reducing the treatment plan completed-session count

#### Scenario: Consultant cancels a session
- **WHEN** a scheduled treatment session cannot happen and the consultant cancels it
- **THEN** the system marks the session as `cancelled` and records cancellation metadata

#### Scenario: Consultant reschedules a session
- **WHEN** a consultant changes the date or time for a session
- **THEN** the system records the session as rescheduled or updates the schedule while preserving reschedule metadata

### Requirement: Customers can view their treatment plans
The system SHALL let customers view treatment plans and sessions that belong to their user account.

#### Scenario: Customer views active plans
- **WHEN** a signed-in customer opens their spa area
- **THEN** the system lists treatment plans where `userId` matches the current Firebase Auth user id

#### Scenario: Customer views plan detail
- **WHEN** a customer opens one of their treatment plans
- **THEN** the system shows copied plan details, consultant information, session timeline, and consultation notes visible to customers

### Requirement: Admins can supervise treatment plans and sessions
The system SHALL let admins view and supervise treatment plans and sessions across customers and consultants.

#### Scenario: Admin views treatment plans
- **WHEN** an admin opens treatment management
- **THEN** the system lists treatment plans across statuses and supports filtering or search

#### Scenario: Admin reassigns consultant
- **WHEN** an admin reassigns a treatment plan or active appointment to another consultant
- **THEN** the system updates consultant assignment metadata while preserving history-relevant timestamps

#### Scenario: Admin reviews no-shows
- **WHEN** an admin filters treatment sessions by no-show status
- **THEN** the system shows sessions marked `no_show` with customer and consultant context
