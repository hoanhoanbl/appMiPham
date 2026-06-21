## ADDED Requirements

### Requirement: Admin configures pooled spa capacity
The system SHALL let admins configure pooled spa booking capacity without requiring named rooms or specialists.

#### Scenario: Admin saves default capacity settings
- **WHEN** an admin saves default spa capacity settings
- **THEN** the system stores concurrent booking capacity, slot block length, working time windows, closed weekdays, booking horizon, and update metadata

#### Scenario: Admin closes a specific date
- **WHEN** an admin marks a date as closed
- **THEN** the system prevents customers from selecting appointment times on that date

#### Scenario: Admin overrides capacity for a specific date
- **WHEN** an admin sets a date-specific concurrent booking capacity
- **THEN** the system uses the override capacity instead of the default capacity for that date

### Requirement: Booking availability uses service duration and remaining capacity
The system SHALL calculate available appointment slots from working hours, current time, package duration, and remaining capacity across all required time blocks.

#### Scenario: Same time allows multiple bookings under capacity
- **WHEN** a slot has fewer active reservations than its configured capacity
- **THEN** the system keeps that slot available for additional customers

#### Scenario: Slot becomes unavailable when any required block is full
- **WHEN** a service duration covers multiple time blocks and at least one required block has reached capacity
- **THEN** the system disables or hides that start time for the customer

#### Scenario: Current-day past slots are unavailable
- **WHEN** a customer views slots for the current date after a slot start time has passed
- **THEN** the system does not allow that past slot to be selected or submitted

#### Scenario: Service cannot exceed working window
- **WHEN** a service duration would end after the configured working window closes
- **THEN** the system does not offer that start time

### Requirement: First appointment booking reserves capacity atomically
The system MUST reserve every required capacity block and create the appointment records in one transaction.

#### Scenario: Customer books a single-session package with capacity available
- **WHEN** a signed-in customer confirms a valid single-session package booking
- **THEN** the system increments booked counts for all required capacity blocks
- **AND** the system creates one pending appointment with copied package, customer, schedule, and capacity fields

#### Scenario: Customer books a treatment package with capacity available
- **WHEN** a signed-in customer confirms a valid multi-session treatment package booking
- **THEN** the system increments booked counts for all required capacity blocks
- **AND** the system creates one pending appointment for the first visit
- **AND** the system creates the treatment plan and treatment session skeletons in the same operation

#### Scenario: Capacity fills during confirmation
- **WHEN** another booking fills one required block before the transaction commits
- **THEN** the system rejects the booking and does not create partial appointment, plan, or session records

### Requirement: Future treatment sessions use the same capacity engine
The system SHALL schedule later treatment sessions through the same capacity validation and reservation rules as first bookings.

#### Scenario: Customer schedules an unscheduled future session
- **WHEN** a customer selects a valid date and time for their unscheduled treatment session
- **THEN** the system reserves all required capacity blocks
- **AND** the system creates a linked appointment
- **AND** the system updates the treatment session schedule fields and status

#### Scenario: Customer cannot schedule an already scheduled session
- **WHEN** a treatment session already has an active appointment and concrete schedule fields
- **THEN** the system prevents the customer from scheduling it again

### Requirement: Capacity fields remain compatible with future resource assignment
The system SHALL store appointment capacity metadata while allowing room or specialist details to remain optional.

#### Scenario: Appointment stores pooled capacity metadata
- **WHEN** the system creates an appointment through capacity booking
- **THEN** the appointment stores capacity units, resource mode, and reserved block keys
- **AND** room or specialist assignment fields may remain blank

#### Scenario: Admin adds internal service notes
- **WHEN** an admin records internal room or service specialist notes on an appointment
- **THEN** the system stores those notes without requiring a specialist account or specialist schedule
