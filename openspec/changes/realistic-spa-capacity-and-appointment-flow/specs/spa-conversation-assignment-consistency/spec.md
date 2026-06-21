## ADDED Requirements

### Requirement: Consultant assignment is distinct from service capacity
The system SHALL treat consultant ownership as customer communication and treatment-tracking responsibility, not as the spa's service capacity limit.

#### Scenario: Multiple appointments share one consultant time period
- **WHEN** multiple customers book the same time under available pooled capacity
- **THEN** the system may assign or allow the same consultant to manage those customer journeys
- **AND** the system does not treat that consultant assignment as a capacity conflict

#### Scenario: Appointment has no named specialist
- **WHEN** an appointment is booked through pooled capacity
- **THEN** the appointment can remain valid without an assigned service specialist record

### Requirement: Treatment journeys use a canonical chat thread
The system SHALL maintain one primary consultant-customer conversation for each treatment journey.

#### Scenario: Consultant claims first appointment of a treatment plan
- **WHEN** a consultant claims the first appointment linked to a treatment plan
- **THEN** the system creates or updates one canonical chat thread for that treatment plan
- **AND** later sessions in the same plan reuse that thread by default

#### Scenario: Consultant chat inbox loads treatment conversations
- **WHEN** the consultant opens the chat inbox
- **THEN** the system lists one row per active treatment conversation instead of one duplicate row per session appointment

#### Scenario: Appointment-only service remains compatible
- **WHEN** a chat thread belongs to a single-session appointment with no treatment plan
- **THEN** the system can continue using the appointment as the conversation context

### Requirement: Reassignment updates all related ownership
The system MUST keep appointment, treatment plan, treatment sessions, progress photos, and chat thread ownership consistent when an admin reassigns a consultant.

#### Scenario: Admin reassigns a treatment plan
- **WHEN** an admin changes the consultant for a treatment plan
- **THEN** the system updates consultant identity fields on the plan, active linked appointments, sessions, visible progress-photo records, and canonical chat thread

#### Scenario: Admin reassigns an appointment-only service
- **WHEN** an admin changes the consultant for an appointment without a treatment plan
- **THEN** the system updates the appointment and appointment chat thread ownership together

#### Scenario: Reassignment preserves conversation history
- **WHEN** consultant ownership changes
- **THEN** existing chat messages remain readable in the same thread according to the new ownership rules

### Requirement: Chat access follows current ownership
The system SHALL allow chat access according to the current assigned consultant and customer ownership.

#### Scenario: Assigned consultant sends a message
- **WHEN** the signed-in consultant matches the chat thread consultant id
- **THEN** the system allows the consultant to read and send messages

#### Scenario: Previous consultant is no longer assigned
- **WHEN** a consultant no longer matches the chat thread consultant id after reassignment
- **THEN** the system prevents that consultant from sending new messages

#### Scenario: Customer reads their treatment conversation
- **WHEN** the signed-in customer owns the treatment plan or appointment linked to the thread
- **THEN** the system allows the customer to read and send messages in that thread
