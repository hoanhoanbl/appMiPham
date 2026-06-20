## ADDED Requirements

### Requirement: Chat opens after consultant assignment
The system SHALL open customer-consultant chat only after an appointment has an assigned consultant.

#### Scenario: Appointment is still pending
- **WHEN** a customer views an appointment with no consultant assigned
- **THEN** the system does not show an active chat composer for that appointment

#### Scenario: Consultant claims appointment
- **WHEN** a consultant successfully claims a pending appointment
- **THEN** the system makes a chat thread available between that customer and consultant

### Requirement: Customers can message the assigned consultant
The system SHALL let customers send and read chat messages for their own claimed appointment or treatment plan.

#### Scenario: Customer sends message
- **WHEN** the appointment belongs to the signed-in customer and has an assigned consultant
- **THEN** the customer can send a text message to the chat thread

#### Scenario: Customer reads messages
- **WHEN** the signed-in customer opens a chat thread tied to their appointment or treatment plan
- **THEN** the system displays messages in chronological order

### Requirement: Consultants can message assigned customers
The system SHALL let consultants send and read chat messages only for appointments or treatment plans assigned to them.

#### Scenario: Consultant sends message
- **WHEN** the signed-in consultant is assigned to the appointment or treatment plan
- **THEN** the consultant can send a text message to the chat thread

#### Scenario: Consultant cannot message unassigned appointment
- **WHEN** a consultant is not assigned to the appointment or treatment plan
- **THEN** the system prevents that consultant from sending messages in the thread

### Requirement: Chat threads preserve participant context
The system SHALL store chat thread participant context for customer, consultant, appointment, and treatment plan when available.

#### Scenario: Thread is created from appointment
- **WHEN** chat is created after an appointment is claimed
- **THEN** the thread stores appointment id, customer id, consultant id, status, and timestamps

#### Scenario: Thread is linked to treatment plan
- **WHEN** a treatment plan is created from a claimed appointment with an existing chat thread
- **THEN** the system can associate the thread with the treatment plan id

### Requirement: Consultants can record consultation notes
The system SHALL let consultants record structured consultation notes after claiming an appointment.

#### Scenario: Consultant saves consultation note
- **WHEN** the assigned consultant enters customer condition, goals, or treatment advice
- **THEN** the system saves the consultation note with consultant id and update timestamp

#### Scenario: Customer views visible consultation note
- **WHEN** a customer opens a treatment plan or appointment detail containing customer-visible consultation notes
- **THEN** the system shows those notes to the customer

### Requirement: Admins can supervise chat and consultation metadata
The system SHALL let admins view chat and consultation ownership metadata for oversight.

#### Scenario: Admin views appointment detail
- **WHEN** an admin opens an appointment or treatment plan detail
- **THEN** the system shows whether a chat thread exists and which consultant owns it

#### Scenario: Admin reassigns consultant
- **WHEN** an admin reassigns an appointment or treatment plan to another consultant
- **THEN** the system updates future chat ownership according to the new consultant assignment
