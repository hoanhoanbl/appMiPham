## ADDED Requirements

### Requirement: Appointment statuses reflect real spa operations
The system SHALL support appointment statuses for pending, assigned, confirmed, checked-in, in-service, completed, cancelled, no-show, and rescheduled states.

#### Scenario: Consultant confirms an assigned appointment
- **WHEN** a consultant or admin confirms an assigned appointment
- **THEN** the system marks the appointment as confirmed and preserves consultant assignment metadata

#### Scenario: Customer arrives before service completion
- **WHEN** the customer arrives for a confirmed appointment
- **THEN** the system allows the appointment to move to checked-in rather than completed

#### Scenario: Service starts after check-in
- **WHEN** staff starts the service for a checked-in appointment
- **THEN** the system allows the appointment to move to in-service

### Requirement: Completion is time-gated
The system MUST prevent future appointments from being completed before service time is valid.

#### Scenario: Consultant attempts to complete a future appointment
- **WHEN** the assigned consultant taps complete before the appointment start time
- **THEN** the system rejects the action and keeps the appointment unfinished

#### Scenario: Consultant completes an in-service appointment
- **WHEN** the assigned consultant completes an appointment that is in-service
- **THEN** the system marks the appointment as completed and records completion metadata

#### Scenario: Treatment session completion updates plan progress once
- **WHEN** a linked treatment session is completed for the first time
- **THEN** the system increments the treatment plan completed-session count once

### Requirement: No-show is time-gated and does not count as completion
The system MUST allow no-show only after the appointment start time plus the configured grace period.

#### Scenario: Consultant marks no-show too early
- **WHEN** the assigned consultant marks no-show before the grace period has passed
- **THEN** the system rejects the action and keeps the appointment active

#### Scenario: Consultant marks no-show after grace period
- **WHEN** the grace period has passed and the customer has not checked in
- **THEN** the system marks the appointment and linked treatment session as no-show
- **AND** the system does not increment completed-session count

### Requirement: Cancellation releases future capacity
The system SHALL release reserved future capacity when an appointment is cancelled before the reserved service time has been consumed.

#### Scenario: Admin cancels a future appointment
- **WHEN** an admin cancels a future active appointment
- **THEN** the system marks the appointment as cancelled
- **AND** the system decrements booked counts for its reserved capacity blocks

#### Scenario: Customer cancels a pending future appointment
- **WHEN** a customer cancels their own pending future appointment
- **THEN** the system releases the reserved capacity blocks and marks the appointment as cancelled

### Requirement: Rescheduling moves capacity atomically
The system MUST release old future capacity blocks and reserve new blocks in one transaction when an appointment is rescheduled.

#### Scenario: Admin reschedules to an available slot
- **WHEN** an admin selects a new valid date and time for an active future appointment
- **THEN** the system releases the old reserved blocks
- **AND** the system reserves the new required blocks
- **AND** the system updates appointment and linked session schedule fields

#### Scenario: Admin reschedules to a full slot
- **WHEN** one or more required new capacity blocks are full
- **THEN** the system rejects the reschedule and keeps the original appointment schedule and capacity reservation

### Requirement: Terminal statuses prevent operational actions
The system SHALL prevent completed, cancelled, and no-show appointments from receiving normal check-in, service-start, completion, or cancellation actions.

#### Scenario: Consultant opens a completed appointment
- **WHEN** a consultant opens an appointment that is already completed
- **THEN** the system shows the final status and hides normal operational action buttons
