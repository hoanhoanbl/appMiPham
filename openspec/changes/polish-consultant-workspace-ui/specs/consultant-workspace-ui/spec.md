## ADDED Requirements

### Requirement: Consultant dashboard separates schedule and messages
The system SHALL present consultant dashboard navigation so schedule operations and customer messages are clearly separate modes.

#### Scenario: Consultant views schedule mode
- **WHEN** a consultant opens the schedule tab
- **THEN** the system shows appointment queues, date/status filters, appointment summaries, and allowed appointment lifecycle actions
- **AND** the schedule copy does not describe the destination as a combined detail/chat/treatment screen

#### Scenario: Consultant views messages mode
- **WHEN** a consultant opens the messages tab
- **THEN** the system shows conversation threads with customer identity, last message or empty conversation state, and treatment or package context when available
- **AND** tapping a conversation opens the dedicated consultant chat screen

### Requirement: Appointment detail is operational only
The system SHALL make consultant appointment detail an operational workspace for booking context, notes, appointment status, treatment sessions, and progress photos.

#### Scenario: Consultant opens appointment detail from schedule
- **WHEN** a consultant taps an appointment from the schedule tab
- **THEN** the system opens an appointment detail screen focused on customer information, booking time, consultation notes, treatment plan context, session status, and valid operational actions
- **AND** the screen does not include an embedded chat transcript or message composer

#### Scenario: Consultant needs to message from appointment detail
- **WHEN** an assigned appointment has an available consultation chat thread
- **THEN** the appointment detail screen provides a clear action to open the dedicated chat screen for that thread
- **AND** sending messages remains handled by the dedicated chat screen

### Requirement: Consultant chat remains message-only
The system SHALL keep the consultant chat screen focused on conversation reading and message sending.

#### Scenario: Consultant opens a chat thread
- **WHEN** the assigned consultant opens a chat thread
- **THEN** the system shows a compact customer header, the message list, and the message composer
- **AND** the screen does not show appointment lifecycle buttons, treatment session cards, progress photo upload controls, or consultation note forms

#### Scenario: Consultant opens customer context
- **WHEN** the consultant taps the customer name, avatar, or profile affordance in the chat header
- **THEN** the system opens the customer profile/history screen for the conversation customer

### Requirement: Consultant screens use polished operational states
The system SHALL provide polished loading, empty, unavailable, and access-denied states across consultant schedule, messages, appointment detail, and chat screens.

#### Scenario: Consultant has no matching appointments
- **WHEN** filters result in no consultant appointments
- **THEN** the system shows an empty state that explains the current filter context without suggesting chat actions

#### Scenario: Consultant cannot access a thread
- **WHEN** the signed-in consultant is not assigned to the requested chat thread
- **THEN** the system shows an access-denied state and disables message sending
