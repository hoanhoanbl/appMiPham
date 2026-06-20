## ADDED Requirements

### Requirement: Admin packages define complete treatment structure
The system SHALL treat admin-created multi-session spa packages as complete treatment packages with fixed customer-facing session structure.

#### Scenario: Admin-created package shows fixed session count
- **WHEN** a customer views a multi-session spa package
- **THEN** the system shows the package image, price, package name, fixed session count, per-session duration, and suggested interval before booking

#### Scenario: Consultant cannot change package session count in normal flow
- **WHEN** a consultant opens a claimed appointment or treatment plan created from an admin package
- **THEN** the system does not provide normal controls for changing the package session count or creating a custom replacement plan

### Requirement: Booking a treatment package creates the treatment journey
The system SHALL create the appointment, treatment plan, and treatment sessions when a customer books a multi-session spa package.

#### Scenario: Customer books first visit for a treatment package
- **WHEN** a signed-in customer selects an available date/time and confirms booking for a multi-session spa package
- **THEN** the system creates one pending appointment for the selected first visit
- **AND** the system creates one treatment plan copied from the admin package with the package session count and customer information
- **AND** the system creates treatment sessions numbered from 1 through the package session count

#### Scenario: First session is linked to first appointment
- **WHEN** the system creates treatment sessions during treatment package booking
- **THEN** session 1 stores the selected date/time, links to the created appointment, and has a scheduled state
- **AND** sessions 2 through N are created without appointment links or concrete schedule fields

#### Scenario: Booking remains atomic
- **WHEN** any required appointment, treatment plan, or treatment session write fails during treatment package booking
- **THEN** the system does not leave a visible partial treatment journey for the customer

### Requirement: Future sessions use unscheduled status
The system SHALL distinguish future sessions that exist in a plan from sessions that have a concrete appointment.

#### Scenario: Future sessions are unscheduled after booking
- **WHEN** a treatment package with more than one session is booked
- **THEN** all future sessions after session 1 have status `unscheduled`

#### Scenario: Legacy empty schedule displays as unscheduled
- **WHEN** an existing treatment session has no concrete schedule fields
- **THEN** the customer and consultant UIs display the session as waiting for scheduling rather than as an active scheduled visit

### Requirement: Consultant claim assigns existing treatment journey
The system SHALL assign the booking's existing treatment plan and sessions when a consultant claims the first pending appointment.

#### Scenario: Consultant claims treatment appointment
- **WHEN** a consultant successfully claims a pending appointment linked to a treatment plan
- **THEN** the appointment stores that consultant's id, email, and display name
- **AND** the linked treatment plan stores the same consultant ownership and becomes active
- **AND** the linked treatment sessions store consultant ownership for query and access control

#### Scenario: Claim opens consultation chat context
- **WHEN** a consultant successfully claims a treatment appointment
- **THEN** the system creates or updates a chat thread with customer, consultant, appointment, and treatment plan context

### Requirement: Customers schedule later sessions from their treatment plan
The system SHALL let customers schedule unscheduled sessions from their treatment plan without allowing booked slots to be reused.

#### Scenario: Customer schedules a future session
- **WHEN** a customer chooses an available date/time for an unscheduled future session in their own treatment plan
- **THEN** the system creates an appointment for that session
- **AND** the system updates the session with appointment id, schedule fields, and status `scheduled`

#### Scenario: Customer cannot select an occupied slot
- **WHEN** a customer views date/time options for a future treatment session
- **THEN** the system hides or disables active appointment slots that are already booked

### Requirement: Treatment package no-show preserves remaining work
The system SHALL record customer no-shows without increasing completed session count.

#### Scenario: Consultant marks treatment session no-show
- **WHEN** the assigned consultant marks a scheduled treatment session as no-show
- **THEN** the system marks the session and related appointment as `no_show`
- **AND** the treatment plan completed-session count does not increase

#### Scenario: Customer can reschedule after no-show
- **WHEN** a treatment session is marked no-show and the package has no payment deduction logic
- **THEN** the system keeps the session visible as unfinished so the customer and consultant can arrange another concrete visit

### Requirement: Single-session spa booking remains lightweight
The system SHALL preserve the existing one-off booking behavior for single-session spa packages.

#### Scenario: Customer books single-session package
- **WHEN** a signed-in customer books an active single-session spa package
- **THEN** the system creates only a pending appointment unless a future requirement explicitly enables treatment tracking for that package type
